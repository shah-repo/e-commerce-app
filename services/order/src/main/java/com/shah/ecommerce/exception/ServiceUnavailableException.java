package com.shah.ecommerce.exception;

import lombok.Getter;

@Getter
public class ServiceUnavailableException extends RuntimeException {
    private final String msg;

    public ServiceUnavailableException(String msg) {
        super(msg);
        this.msg = msg;
    }
}
