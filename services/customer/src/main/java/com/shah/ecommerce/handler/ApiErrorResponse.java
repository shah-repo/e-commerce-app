package com.shah.ecommerce.handler;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {
}
