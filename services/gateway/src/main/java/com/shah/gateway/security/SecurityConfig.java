package com.shah.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static com.shah.gateway.security.Roles.ADMIN_ROLE;
import static com.shah.gateway.security.Roles.SERVICE_ROLE;
import static com.shah.gateway.security.Roles.USER_ROLE;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String ROLES_CLAIM = "roles";

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationEntryPoint authenticationEntryPoint, JwtAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .pathMatchers("/api/v1/customers", "/api/v1/customers/**").hasAnyAuthority(ADMIN_ROLE, SERVICE_ROLE)
                        .pathMatchers("/api/v1/order-lines", "/api/v1/order-lines/**").hasAnyAuthority(ADMIN_ROLE, SERVICE_ROLE)
                        .pathMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/").hasAnyAuthority(ADMIN_ROLE, SERVICE_ROLE)
                        .pathMatchers("/api/v1/products/purchase/rollback").hasAnyAuthority(ADMIN_ROLE, SERVICE_ROLE)
                        .pathMatchers(
                                "/api/v1/orders", "/api/v1/orders/**",
                                "/api/v1/products", "/api/v1/products/**",
                                "/api/v1/payments", "/api/v1/payments/**"
                        ).hasAnyAuthority(ADMIN_ROLE, USER_ROLE, SERVICE_ROLE)
                        .anyExchange().hasAnyAuthority(ADMIN_ROLE, SERVICE_ROLE)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${application.security.jwt.secret-key}") String secretKey) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(secretKey), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(ROLES_CLAIM);
        authoritiesConverter.setAuthorityPrefix("");

        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return new ReactiveJwtAuthenticationConverterAdapter(authenticationConverter);
    }
}
