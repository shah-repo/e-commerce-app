package com.shah.ecommerce.exception;

import lombok.Getter;

@Getter
public class InvalidCredentialsException extends RuntimeException {
    private final String msg;

    public InvalidCredentialsException(String msg) {
        super(msg);
        this.msg = msg;
    }
}
