package com.shah.ecommerce.auth;

import com.shah.ecommerce.exception.UserAlreadyExistsException;
import com.shah.ecommerce.security.JwtService;
import com.shah.ecommerce.user.Role;
import com.shah.ecommerce.user.User;
import com.shah.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password())
        );
        var user = (User) authentication.getPrincipal();
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(String.format("An account already exists with the given email:: %s", email));
        }
        var user = userRepository.save(
                User.builder()
                        .firstname(request.firstname())
                        .lastname(request.lastname())
                        .email(email)
                        .password(passwordEncoder.encode(request.password()))
                        .role(Role.USER_ROLE)
                        .build()
        );
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateToken(user),
                TOKEN_TYPE,
                jwtService.getExpirationInSeconds(),
                user.getRole().getAuthority()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
