package com.shah.gateway.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
                "application.security.jwt.secret-key=" + SecurityConfigTests.SECRET_KEY
        }
)
@AutoConfigureWebTestClient
class SecurityConfigTests {

    static final String SECRET_KEY = "+rplLsk+ZtNlLp+S2M7zrTu2HWVZQ0miRXGW6BvvfhQ9mOK4FtQfkkh35/Ssgz5l";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldRejectRequestWithoutToken() {
        webTestClient.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").value(Matchers.containsString("login"));
    }

    @Test
    void shouldRejectExpiredToken() {
        var token = token(Roles.ADMIN_ROLE, Instant.now().minusSeconds(120));
        webTestClient.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Your session has expired. Please login again to get a new token");
    }

    @Test
    void shouldRejectTamperedToken() {
        var token = token(Roles.ADMIN_ROLE, Instant.now().plusSeconds(300)) + "x";
        webTestClient.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowLoginWithoutToken() {
        webTestClient.post().uri("/api/v1/auth/login")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldAllowAdminToAccessEveryApi() {
        assertStatus(Roles.ADMIN_ROLE, HttpMethod.GET, "/api/v1/customers", 404);
        assertStatus(Roles.ADMIN_ROLE, HttpMethod.GET, "/api/v1/order-lines/orders/1", 404);
        assertStatus(Roles.ADMIN_ROLE, HttpMethod.GET, "/api/v1/orders", 404);
        assertStatus(Roles.ADMIN_ROLE, HttpMethod.POST, "/api/v1/orders", 404);
        assertStatus(Roles.ADMIN_ROLE, HttpMethod.GET, "/api/v1/products", 404);
        assertStatus(Roles.ADMIN_ROLE, HttpMethod.POST, "/api/v1/payments", 404);
    }

    @Test
    void shouldRestrictUserRole() {
        assertStatus(Roles.USER_ROLE, HttpMethod.GET, "/api/v1/customers", 403);
        assertStatus(Roles.USER_ROLE, HttpMethod.POST, "/api/v1/customers", 403);
        assertStatus(Roles.USER_ROLE, HttpMethod.GET, "/api/v1/customers/123", 403);
        assertStatus(Roles.USER_ROLE, HttpMethod.GET, "/api/v1/order-lines/orders/1", 403);
        assertStatus(Roles.USER_ROLE, HttpMethod.GET, "/api/v1/orders", 403);
        assertStatus(Roles.USER_ROLE, HttpMethod.POST, "/api/v1/products/purchase/rollback", 403);
        assertStatus(Roles.USER_ROLE, HttpMethod.GET, "/customer-service/api/v1/customers", 403);
    }

    @Test
    void shouldAllowUserRoleOnPermittedApis() {
        assertStatus(Roles.USER_ROLE, HttpMethod.POST, "/api/v1/orders", 404);
        assertStatus(Roles.USER_ROLE, HttpMethod.GET, "/api/v1/orders/1", 404);
        assertStatus(Roles.USER_ROLE, HttpMethod.GET, "/api/v1/products", 404);
        assertStatus(Roles.USER_ROLE, HttpMethod.POST, "/api/v1/products/purchase", 404);
        assertStatus(Roles.USER_ROLE, HttpMethod.POST, "/api/v1/payments", 404);
    }

    @Test
    void shouldAllowServiceRoleForInternalCalls() {
        assertStatus(Roles.SERVICE_ROLE, HttpMethod.GET, "/api/v1/customers/123", 404);
        assertStatus(Roles.SERVICE_ROLE, HttpMethod.POST, "/api/v1/products/purchase/rollback", 404);
    }

    private void assertStatus(String role, HttpMethod method, String uri, int expectedStatus) {
        webTestClient.method(method).uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(role, Instant.now().plusSeconds(300)))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }

    private String token(String role, Instant expiresAt) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(SECRET_KEY), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder()
                .subject("test@ecommerce.com")
                .issuedAt(expiresAt.minusSeconds(600))
                .expiresAt(expiresAt)
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
