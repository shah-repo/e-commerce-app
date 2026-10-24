package com.shah.ecommerce.user;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Role {
    admin_role,
    user_role;

    @JsonCreator
    public static Role fromValue(String value) {
        if (value == null || value.isBlank()) {
            return user_role;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "admin_role", "role_admin_role", "admin", "role_admin" -> admin_role;
            case "user_role", "role_user_role", "user", "role_user" -> user_role;
            default -> throw new IllegalArgumentException(
                    String.format("Invalid role '%s'. Supported roles are 'admin_role' and 'user_role'.", value)
            );
        };
    }
}
