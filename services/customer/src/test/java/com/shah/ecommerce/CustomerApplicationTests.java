package com.shah.ecommerce;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shah.ecommerce.customer.CustomerController;
import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.customer.CustomerService;
import com.shah.ecommerce.exception.CustomerNotFoundException;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, GLobalExceptionHandler.class})
class CustomerApplicationTests {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

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
    void shouldEnforceAdminOnlyAccessOnCustomersAndReturnReadableErrors() throws Exception {
        when(customerService.findAllCustomer()).thenReturn(List.of(
                new CustomerResponse("c1", "Shah", "User", "shah@test.com", null)
        ));
        when(customerService.findById("missing"))
                .thenThrow(new CustomerNotFoundException("No customer found with the provided ID: missing"));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("user_role")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("admin_role")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("c1"));

        mockMvc.perform(get("/api/v1/customers/missing")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("admin_role")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No customer found with the provided ID: missing"));
    }
}
