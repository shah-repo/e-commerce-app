package com.shah.ecommerce.config;

import com.shah.ecommerce.user.Role;
import com.shah.ecommerce.user.User;
import com.shah.ecommerce.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${application.security.default-admin.email}")
    private String adminEmail;
    @Value("${application.security.default-admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        var email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(
                User.builder()
                        .firstname("Admin")
                        .lastname("User")
                        .email(email)
                        .password(passwordEncoder.encode(adminPassword))
                        .role(Role.ADMIN_ROLE)
                        .build()
        );
        log.info("Default admin account created with email <{}>", email);
    }
}
