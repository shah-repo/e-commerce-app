package com.shah.ecommerce.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shah.ecommerce.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class JwtService {

    private static final String ISSUER = "auth-service";

    private final JwtEncoder jwtEncoder;
    private final long expiration;

    public JwtService(
            @Value("${application.security.jwt.secret-key}") String secretKey,
            @Value("${application.security.jwt.expiration}") long expiration
    ) {
        var key = new SecretKeySpec(Base64.getDecoder().decode(secretKey), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.expiration = expiration;
    }

    public String generateToken(User user) {
        var issuedAt = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusMillis(expiration))
                .claim("userId", user.getId())
                .claim("name", String.format("%s %s", user.getFirstname(), user.getLastname()).trim())
                .claim("roles", List.of(user.getRole().getAuthority()))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpirationInSeconds() {
        return expiration / 1000;
    }
}
