package com.shah.ecommerce.handler;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> errors
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }

    public ErrorResponse(Map<String, String> errors) {
        this(400, "Validation Failed", "Request validation failed. Please check the provided fields.", errors);
    }
}
