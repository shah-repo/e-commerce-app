package com.shah.ecommerce.auth;

import com.shah.ecommerce.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        String username,
        @Email(message = "Please provide a valid email address")
        String email,
        @NotBlank(message = "Password is required")
        String password,
        @NotNull(message = "Role is required (admin_role or user_role)")
        Role role
) {
}
