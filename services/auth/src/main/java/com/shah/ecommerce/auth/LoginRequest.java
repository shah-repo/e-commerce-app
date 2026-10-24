package com.shah.ecommerce.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String username,
        String email,
        @NotBlank(message = "Password is required")
        String password
) {
    public String identifier() {
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return null;
    }
}
