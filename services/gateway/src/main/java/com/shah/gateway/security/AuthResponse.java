package com.shah.gateway.security;

import lombok.Builder;

@Builder
public record AuthResponse(
        String token,
        String accessToken,
        String username,
        String role
) {
}
