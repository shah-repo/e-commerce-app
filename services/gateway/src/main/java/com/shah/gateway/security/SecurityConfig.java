package com.shah.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shah.gateway.handler.ApiErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final String secretKey;

    public SecurityConfig(
            ObjectMapper objectMapper,
            @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secretKey
    ) {
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/v1/auth/**", "/auth-service/api/v1/auth/**").permitAll()
                        .pathMatchers(
                                "/api/v1/customers",
                                "/api/v1/customers/**",
                                "/*/api/v1/customers",
                                "/*/api/v1/customers/**"
                        ).hasAuthority("admin_role")
                        .pathMatchers(
                                "/api/v1/order-lines",
                                "/api/v1/order-lines/**",
                                "/*/api/v1/order-lines",
                                "/*/api/v1/order-lines/**"
                        ).hasAuthority("admin_role")
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/orders",
                                "/api/v1/orders/**",
                                "/*/api/v1/orders",
                                "/*/api/v1/orders/**"
                        ).hasAuthority("admin_role")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(reactiveJwtDecoder())
                                .jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())
                        )
                        .authenticationEntryPoint((exchange, ex) -> writeErrorResponse(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication required. Please provide a valid Bearer JWT token."
                        ))
                        .accessDeniedHandler((exchange, denied) -> writeErrorResponse(
                                exchange,
                                HttpStatus.FORBIDDEN,
                                "Access denied. Your role does not have permission to access this resource."
                        ))
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((exchange, authEx) -> writeErrorResponse(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "Authentication required. Please provide a valid Bearer JWT token."
                        ))
                        .accessDeniedHandler((exchange, denied) -> writeErrorResponse(
                                exchange,
                                HttpStatus.FORBIDDEN,
                                "Access denied. Your role does not have permission to access this resource."
                        ))
                )
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKey key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
            }
            String singleRole = jwt.getClaimAsString("role");
            if (singleRole != null && authorities.stream().noneMatch(a -> a.getAuthority().equals(singleRole))) {
                authorities.add(new SimpleGrantedAuthority(singleRole));
            }
            return new ArrayList<>(authorities);
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiErrorResponse errorBody = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value(),
                Instant.now().toString()
        );
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
