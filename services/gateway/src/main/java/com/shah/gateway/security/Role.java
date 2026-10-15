package com.shah.gateway.security;

public enum Role {
    admin_role,
    user_role;

    public static Role fromString(String value) {
        if (value == null || value.isBlank()) {
            return user_role;
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.equals("admin") || normalized.equals("admin_role") || normalized.equals("role_admin") || normalized.equals("role_admin_role")) {
            return admin_role;
        }
        return user_role;
    }
}
