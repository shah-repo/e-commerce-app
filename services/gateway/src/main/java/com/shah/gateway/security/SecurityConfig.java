package com.shah.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKey secretKey = new SecretKeySpec(jwtService.getSigningKey(), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/login/**",
                                "/api/v1/register/**",
                                "/auth/**",
                                "/login/**",
                                "/register/**",
                                "/eureka/**"
                        ).permitAll()
                        .pathMatchers("/api/v1/customers", "/api/v1/customers/**").hasAuthority("admin_role")
                        .pathMatchers("/api/v1/order-lines", "/api/v1/order-lines/**").hasAuthority("admin_role")
                        .pathMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/**").hasAuthority("admin_role")
                        .pathMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/orders/**").hasAnyAuthority("admin_role", "user_role")
                        .pathMatchers("/api/v1/products", "/api/v1/products/**").hasAnyAuthority("admin_role", "user_role")
                        .pathMatchers("/api/v1/payments", "/api/v1/payments/**").hasAnyAuthority("admin_role", "user_role")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                            byte[] bytes = "Unauthorized: Missing or invalid JWT token. Please login first.".getBytes(StandardCharsets.UTF_8);
                            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.FORBIDDEN);
                            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                            byte[] bytes = "Access Denied: You do not have permission to access this resource.".getBytes(StandardCharsets.UTF_8);
                            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
                        })
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.UNAUTHORIZED);
                            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                            byte[] bytes = "Unauthorized: Missing or invalid JWT token. Please login first.".getBytes(StandardCharsets.UTF_8);
                            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            var response = exchange.getResponse();
                            response.setStatusCode(HttpStatus.FORBIDDEN);
                            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                            byte[] bytes = "Access Denied: You do not have permission to access this resource.".getBytes(StandardCharsets.UTF_8);
                            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
                        })
                )
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            extractAuthorities(jwt, authorities);
            return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
        };
    }

    private void extractAuthorities(Jwt jwt, List<GrantedAuthority> authorities) {
        Object roleClaim = jwt.getClaims().get("role");
        if (roleClaim != null) {
            addRoleAuthority(authorities, roleClaim.toString());
        }
        Object rolesClaim = jwt.getClaims().get("roles");
        if (rolesClaim instanceof Collection<?> roles) {
            for (Object r : roles) {
                if (r != null) {
                    addRoleAuthority(authorities, r.toString());
                }
            }
        }
    }

    private void addRoleAuthority(List<GrantedAuthority> authorities, String rawRole) {
        Role normalized = Role.fromString(rawRole);
        authorities.add(new SimpleGrantedAuthority(normalized.name()));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalized.name()));
        authorities.add(new SimpleGrantedAuthority(rawRole));
    }
}
