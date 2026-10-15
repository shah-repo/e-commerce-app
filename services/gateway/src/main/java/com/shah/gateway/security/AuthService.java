package com.shah.gateway.security;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();

    public record UserAccount(String username, String password, Role role) {}

    @PostConstruct
    public void initDefaultUsers() {
        registerUserInternal("admin", "admin", Role.admin_role);
        registerUserInternal("admin@ecommerce.com", "admin", Role.admin_role);
        registerUserInternal("admin_user", "admin123", Role.admin_role);
        registerUserInternal("user", "user", Role.user_role);
        registerUserInternal("user@ecommerce.com", "user", Role.user_role);
        registerUserInternal("normal_user", "user123", Role.user_role);
    }

    private void registerUserInternal(String username, String rawPassword, Role role) {
        users.put(username, new UserAccount(username, passwordEncoder.encode(rawPassword), role));
    }

    public AuthResponse login(AuthRequest request) {
        UserAccount account = users.get(request.username());
        if (account == null || !passwordEncoder.matches(request.password(), account.password())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String token = jwtService.generateToken(account.username(), account.role());
        return AuthResponse.builder()
                .token(token)
                .accessToken(token)
                .username(account.username())
                .role(account.role().name())
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        Role role = Role.fromString(request.role());
        registerUserInternal(request.username(), request.password(), role);
        String token = jwtService.generateToken(request.username(), role);
        return AuthResponse.builder()
                .token(token)
                .accessToken(token)
                .username(request.username())
                .role(role.name())
                .build();
    }
}
