package com.shah.ecommerce.auth;

import com.shah.ecommerce.user.Role;

public record AuthResponse(
        String token,
        String tokenType,
        String username,
        Role role,
        long expiresIn
) {
}
