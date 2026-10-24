package com.shah.ecommerce.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AuthService authService;

    @Override
    public void run(String... args) {
        try {
            authService.ensureDefaultUsers();
            log.info("Default users verified in authentication database");
        } catch (Exception ex) {
            log.warn("Deferred default user initialization until authentication database becomes reachable: {}", ex.getMessage());
        }
    }
}
