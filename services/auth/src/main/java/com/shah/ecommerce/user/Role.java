package com.shah.ecommerce.user;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Role {
    admin_role,
    user_role;

    @JsonCreator
    public static Role fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role must not be empty. Allowed values: admin_role, user_role");
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "admin_role", "admin" -> admin_role;
            case "user_role", "user" -> user_role;
            default -> throw new IllegalArgumentException(
                    "Invalid role '" + value + "'. Allowed values: admin_role, user_role"
            );
        };
    }
}
