package com.shah.ecommerce.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    ADMIN_ROLE("admin_role"),
    USER_ROLE("user_role");

    private final String authority;
}
