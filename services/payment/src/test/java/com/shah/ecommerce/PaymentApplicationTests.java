package com.shah.ecommerce;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shah.ecommerce.exception.ServiceUnavailableException;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
import com.shah.ecommerce.payment.Customer;
import com.shah.ecommerce.payment.PaymentController;
import com.shah.ecommerce.payment.PaymentMethod;
import com.shah.ecommerce.payment.PaymentRequest;
import com.shah.ecommerce.payment.PaymentService;
import com.shah.ecommerce.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, GLobalExceptionHandler.class})
class PaymentApplicationTests {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

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
    void shouldProtectPaymentsAndReturnHumanReadableErrorOnKafkaOutage() throws Exception {
        PaymentRequest req = new PaymentRequest(
                1,
                new BigDecimal("250.00"),
                10,
                "ORD-1001",
                PaymentMethod.CREDIT_CARD,
                new Customer("c1", "Shah", "Dev", "shah@test.com")
        );

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        when(paymentService.createPayment(any())).thenReturn(55);
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("user_role"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/payments/55")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("admin_role")))
                .andExpect(status().isNoContent());

        when(paymentService.createPayment(any()))
                .thenThrow(new ServiceUnavailableException("Notification Messaging Service (Kafka) is currently unavailable. Please try again later."));
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("user_role"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
