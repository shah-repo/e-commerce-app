package com.shah.ecommerce;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
import com.shah.ecommerce.product.*;
import com.shah.ecommerce.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductControler.class)
@Import({SecurityConfig.class, GLobalExceptionHandler.class})
class ProductApplicationTests {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private String token(String role) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .subject("tester")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .claim("role", role)
                        .claim("roles", List.of(role))
                        .build()
        )).getTokenValue();
    }

    @Test
    void shouldRequireJwtAndAllowBothAdminAndUserRolesOnProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("user_role")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("admin_role")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldPurchaseAndReleaseProductQuantitiesProperly() {
        ProductRepository repo = mock(ProductRepository.class);
        ProductMapper mapper = new ProductMapper();
        ProductService service = new ProductService(repo, mapper);

        Product laptop = Product.builder()
                .id(1)
                .name("Laptop")
                .description("Pro Laptop")
                .availableQuantity(10.0)
                .price(new BigDecimal("1200.00"))
                .build();

        when(repo.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(laptop));
        when(repo.findById(1)).thenReturn(Optional.of(laptop));

        service.purchaseProduct(List.of(new ProductPurchaseRequest(1, 3.0)));
        assertThat(laptop.getAvailableQuantity()).isEqualTo(7.0);

        service.releaseProducts(List.of(new ProductPurchaseRequest(1, 3.0)));
        assertThat(laptop.getAvailableQuantity()).isEqualTo(10.0);
    }
}
