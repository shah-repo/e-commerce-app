package com.shah.ecommerce.security;

import com.shah.ecommerce.user.Role;
import com.shah.ecommerce.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {

    private static final String SECRET_KEY = "+rplLsk+ZtNlLp+S2M7zrTu2HWVZQ0miRXGW6BvvfhQ9mOK4FtQfkkh35/Ssgz5l";

    private final JwtService jwtService = new JwtService(SECRET_KEY, 3_600_000);

    @Test
    void shouldGenerateHs256TokenWithRoleClaim() {
        var user = User.builder()
                .id(1)
                .firstname("John")
                .lastname("Doe")
                .email("john@doe.com")
                .role(Role.USER_ROLE)
                .build();

        var token = jwtService.generateToken(user);

        var key = new SecretKeySpec(Base64.getDecoder().decode(SECRET_KEY), "HmacSHA256");
        var jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
        assertThat(jwt.getSubject()).isEqualTo("john@doe.com");
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("user_role"));
        assertThat(jwtService.getExpirationInSeconds()).isEqualTo(3600);
    }
}
