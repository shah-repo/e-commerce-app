package com.shah.gateway.handler;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                "spring.cloud.gateway.discovery.locator.enabled=false",
                "spring.cloud.gateway.routes[0].id=customer-service",
                "spring.cloud.gateway.routes[0].uri=lb://customer-service",
                "spring.cloud.gateway.routes[0].predicates[0]=Path=/api/v1/customers/**",
                "spring.cloud.gateway.routes[1].id=product-service",
                "spring.cloud.gateway.routes[1].uri=http://localhost:1",
                "spring.cloud.gateway.routes[1].predicates[0]=Path=/api/v1/products/**",
                "application.security.jwt.secret-key=" + GatewayExceptionHandlerTests.SECRET_KEY
        }
)
@AutoConfigureWebTestClient
class GatewayExceptionHandlerTests {

    static final String SECRET_KEY = "+rplLsk+ZtNlLp+S2M7zrTu2HWVZQ0miRXGW6BvvfhQ9mOK4FtQfkkh35/Ssgz5l";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnReadableErrorWhenNoServiceInstanceIsAvailable() {
        webTestClient.get().uri("/api/v1/customers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Customer service is currently unavailable. Please try again later")
                .jsonPath("$.path").isEqualTo("/api/v1/customers");
    }

    @Test
    void shouldReturnReadableErrorWhenServiceRefusesConnection() {
        webTestClient.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Localhost is currently unavailable. Please try again later");
    }

    @Test
    void shouldReturnReadableErrorWhenRouteDoesNotExist() {
        webTestClient.get().uri("/api/v1/unknown")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("The requested resource /api/v1/unknown was not found");
    }

    private String adminToken() {
        var key = new SecretKeySpec(Base64.getDecoder().decode(SECRET_KEY), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder()
                .subject("admin@ecommerce.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of("admin_role"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
