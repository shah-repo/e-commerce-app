package com.shah.ecommerce.auth;

import com.shah.ecommerce.exception.InvalidCredentialsException;
import com.shah.ecommerce.exception.UserAlreadyExistsException;
import com.shah.ecommerce.user.AppUser;
import com.shah.ecommerce.user.Role;
import com.shah.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        ensureDefaultUsers();

        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException(
                    String.format("Username '%s' is already registered. Please choose a different username.", username)
            );
        }

        String email = (request.email() != null && !request.email().isBlank()) ? request.email().trim() : null;
        if (email != null && userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(
                    String.format("Email '%s' is already registered. Please use a different email address.", email)
            );
        }

        Role assignedRole = request.role() != null ? request.role() : Role.user_role;

        AppUser savedUser = userRepository.save(
                AppUser.builder()
                        .username(username)
                        .email(email)
                        .password(passwordEncoder.encode(request.password()))
                        .role(assignedRole)
                        .build()
        );

        String token = jwtService.generateToken(savedUser);
        return new AuthResponse(token, "Bearer", savedUser.getUsername(), savedUser.getRole(), jwtService.getExpirationSeconds());
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.identifier();
        if (identifier == null) {
            throw new InvalidCredentialsException("Username or email is required to log in.");
        }

        ensureDefaultUsers();

        AppUser user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid username/email or password. Please verify your credentials and try again."
                ));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException(
                    "Invalid username/email or password. Please verify your credentials and try again."
            );
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", user.getUsername(), user.getRole(), jwtService.getExpirationSeconds());
    }

    public synchronized void ensureDefaultUsers() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(
                    AppUser.builder()
                            .username("admin")
                            .email("admin@ecommerce.com")
                            .password(passwordEncoder.encode("admin123"))
                            .role(Role.admin_role)
                            .build()
            );
        }
        if (!userRepository.existsByUsername("user")) {
            userRepository.save(
                    AppUser.builder()
                            .username("user")
                            .email("user@ecommerce.com")
                            .password(passwordEncoder.encode("user123"))
                            .role(Role.user_role)
                            .build()
            );
        }
    }
}
