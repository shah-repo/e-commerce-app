package com.shah.ecommerce.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Component
public class ServiceTokenProvider {

    private static final String SERVICE_NAME = "order-service";
    private static final String SERVICE_ROLE = "service_role";
    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(15);
    private static final Duration RENEWAL_MARGIN = Duration.ofMinutes(1);

    private final JwtEncoder jwtEncoder;
    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(@Value("${application.security.jwt.secret-key}") String secretKey) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(secretKey), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    public synchronized String getToken() {
        if (token == null || Instant.now().isAfter(expiresAt.minus(RENEWAL_MARGIN))) {
            var issuedAt = Instant.now();
            expiresAt = issuedAt.plus(TOKEN_VALIDITY);
            var claims = JwtClaimsSet.builder()
                    .issuer(SERVICE_NAME)
                    .subject(SERVICE_NAME)
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .claim("roles", List.of(SERVICE_ROLE))
                    .build();
            var header = JwsHeader.with(MacAlgorithm.HS256).build();
            token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }
        return token;
    }
}
