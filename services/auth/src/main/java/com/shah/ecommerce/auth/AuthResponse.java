package com.shah.ecommerce.auth;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String role
) {
}
