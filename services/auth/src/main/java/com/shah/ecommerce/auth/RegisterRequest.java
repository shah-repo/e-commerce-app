package com.shah.ecommerce.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Firstname is required")
        String firstname,
        @NotBlank(message = "Lastname is required")
        String lastname,
        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password should be at least 8 characters long")
        String password
) {
}
