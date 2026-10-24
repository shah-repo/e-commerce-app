package com.shah.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${application.security.jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKey key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverterAdapter reactiveJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        String role = jwt.getClaimAsString("role");
        if (role != null && !role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority(role));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String r : roles) {
                if (r != null && !r.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(r));
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
                }
            }
        }
        return authorities;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/v1/auth/**", "/*/api/v1/auth/**").permitAll()
                        .pathMatchers(
                                "/api/v1/customers", "/api/v1/customers/**",
                                "/*/api/v1/customers", "/*/api/v1/customers/**"
                        ).hasAuthority("admin_role")
                        .pathMatchers(
                                "/api/v1/order-lines", "/api/v1/order-lines/**",
                                "/*/api/v1/order-lines", "/*/api/v1/order-lines/**"
                        ).hasAuthority("admin_role")
                        .pathMatchers(
                                HttpMethod.GET,
                                "/api/v1/orders", "/api/v1/orders/**",
                                "/*/api/v1/orders", "/*/api/v1/orders/**"
                        ).hasAuthority("admin_role")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter()))
                        .authenticationEntryPoint((exchange, ex) -> {
                            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                            String message = (authHeader == null || authHeader.isBlank())
                                    ? "Authentication required. Please log in at /api/v1/auth/login and provide 'Authorization: Bearer <token>'."
                                    : "Invalid or expired JWT token. Please log in again at /api/v1/auth/login to obtain a valid token.";
                            return writeJsonError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, "Unauthorized", message);
                        })
                        .accessDeniedHandler((exchange, denied) -> {
                            String method = exchange.getRequest().getMethod().name();
                            String path = exchange.getRequest().getURI().getPath();
                            String message = String.format(
                                    "Access denied. Your role does not have permission to perform %s '%s'. Required role: admin_role.",
                                    method, path
                            );
                            return writeJsonError(exchange, objectMapper, HttpStatus.FORBIDDEN, "Forbidden", message);
                        })
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> writeJsonError(
                                exchange,
                                objectMapper,
                                HttpStatus.UNAUTHORIZED,
                                "Unauthorized",
                                "Authentication required. Please log in at /api/v1/auth/login and provide 'Authorization: Bearer <token>'."
                        ))
                        .accessDeniedHandler((exchange, denied) -> {
                            String method = exchange.getRequest().getMethod().name();
                            String path = exchange.getRequest().getURI().getPath();
                            String message = String.format(
                                    "Access denied. Your role does not have permission to perform %s '%s'. Required role: admin_role.",
                                    method, path
                            );
                            return writeJsonError(exchange, objectMapper, HttpStatus.FORBIDDEN, "Forbidden", message);
                        })
                )
                .build();
    }

    private Mono<Void> writeJsonError(
            ServerWebExchange exchange,
            ObjectMapper objectMapper,
            HttpStatus status,
            String error,
            String message
    ) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", exchange.getRequest().getURI().getPath());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
