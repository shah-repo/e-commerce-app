package com.shah.gateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false"
        }
)
@AutoConfigureWebTestClient
class GatewaySecurityAndErrorHandlingTests {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private WebTestClient webTestClient;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.adminToken = generateToken(encoder, "admin", "admin_role");
        this.userToken = generateToken(encoder, "user", "user_role");
    }

    private String generateToken(JwtEncoder encoder, String username, String role) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", role)
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void unauthenticatedRequestsAreBlockedWithHumanReadable401() {
        webTestClient.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.message").exists();

        webTestClient.get().uri("/api/v1/customers")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void userRoleCannotAccessRestrictedEndpointsAndReceivesReadable403() {
        webTestClient.get().uri("/api/v1/customers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.message").exists();

        webTestClient.get().uri("/api/v1/order-lines/orders/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403);

        webTestClient.get().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403);

        webTestClient.get().uri("/api/v1/orders/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void userRolePassesAuthOnCreateOrderAndProductsAndDownstreamDownReturnsReadable503() {
        webTestClient.post().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "paymentMethod": "CREDIT_CARD",
                          "customerId": "cust-1",
                          "products": [{"productId": 1, "quantity": 1}]
                        }
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.message").isEqualTo("Order service is currently unavailable. Please ensure the service is running and try again later.");

        webTestClient.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Product service is currently unavailable. Please ensure the service is running and try again later.");
    }

    @Test
    void adminRolePassesAuthorizationOnAllEndpoints() {
        webTestClient.get().uri("/api/v1/customers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Customer service is currently unavailable. Please ensure the service is running and try again later.");

        webTestClient.get().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        webTestClient.get().uri("/api/v1/order-lines/orders/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
