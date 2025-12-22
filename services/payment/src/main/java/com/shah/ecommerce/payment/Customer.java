package com.shah.ecommerce.payment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@Validated // it will consider all the attributes inside the customer class while running spring validation dependency
public record Customer(
        String id,
        @NotNull(message = "Firstname is required")
        String firstName,
        @NotNull(message = "Lastname is required")
        String lastName,
        @NotNull(message = "Email is required")
        @Email(message = "Email should be valid")
        String email
) {
}
