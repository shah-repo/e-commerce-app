package com.shah.ecommerce.exception;

import lombok.Getter;

@Getter
public class UserAlreadyExistsException extends RuntimeException {
    private final String msg;

    public UserAlreadyExistsException(String msg) {
        super(msg);
        this.msg = msg;
    }
}
