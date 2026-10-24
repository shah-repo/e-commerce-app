package com.shah.ecommerce.auth;

import com.shah.ecommerce.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        String username,
        @Email(message = "Please enter a valid email address")
        String email,
        @NotBlank(message = "Password is required")
        String password,
        Role role
) {
}
