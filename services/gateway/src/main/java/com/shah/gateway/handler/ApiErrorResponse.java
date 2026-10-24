package com.shah.gateway.handler;

public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        String timestamp
) {
}
