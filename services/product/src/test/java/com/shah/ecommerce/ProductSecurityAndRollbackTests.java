package com.shah.ecommerce;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shah.ecommerce.config.SecurityConfig;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
import com.shah.ecommerce.product.Product;
import com.shah.ecommerce.product.ProductControler;
import com.shah.ecommerce.product.ProductMapper;
import com.shah.ecommerce.product.ProductRepository;
import com.shah.ecommerce.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductControler.class)
@Import({ProductService.class, ProductMapper.class, SecurityConfig.class, GLobalExceptionHandler.class})
class ProductSecurityAndRollbackTests {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductRepository productRepository;

    private String userToken;
    private Product laptop;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "user_role")
                .claim("roles", List.of("user_role"))
                .build();
        this.userToken = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        this.laptop = Product.builder()
                .id(1)
                .name("Laptop")
                .description("High performance laptop")
                .availableQuantity(10.0)
                .price(new BigDecimal("1500.00"))
                .build();

        when(productRepository.findAllByIdInOrderById(List.of(1))).thenReturn(List.of(laptop));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void purchaseAndRollbackPurchaseRestoreStockCleanly() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/products/purchase")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\": 1, \"quantity\": 3}]"))
                .andExpect(status().isOk());

        assertThat(laptop.getAvailableQuantity()).isEqualTo(7.0);

        mockMvc.perform(post("/api/v1/products/rollback-purchase")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"productId\": 1, \"quantity\": 3}]"))
                .andExpect(status().isOk());

        assertThat(laptop.getAvailableQuantity()).isEqualTo(10.0);
    }
}
