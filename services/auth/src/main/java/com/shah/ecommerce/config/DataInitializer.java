package com.shah.ecommerce.config;

import com.shah.ecommerce.user.Role;
import com.shah.ecommerce.user.User;
import com.shah.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            if (!userRepository.existsByUsername("admin")) {
                userRepository.save(
                        User.builder()
                                .username("admin")
                                .email("admin@ecommerce.com")
                                .password(passwordEncoder.encode("admin123"))
                                .role(Role.admin_role)
                                .build()
                );
                log.info("Initialized default admin account: username='admin', role='admin_role'");
            }
            if (!userRepository.existsByUsername("user")) {
                userRepository.save(
                        User.builder()
                                .username("user")
                                .email("user@ecommerce.com")
                                .password(passwordEncoder.encode("user123"))
                                .role(Role.user_role)
                                .build()
                );
                log.info("Initialized default user account: username='user', role='user_role'");
            }
        } catch (Exception ex) {
            log.warn("Could not seed default users at startup: {}", ex.getMessage());
        }
    }
}
