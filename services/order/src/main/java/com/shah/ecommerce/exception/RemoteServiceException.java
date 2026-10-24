package com.shah.ecommerce.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

@EqualsAndHashCode(callSuper = true)
@Data
public class RemoteServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String msg;
}
