package com.shah.ecommerce.handler;

import com.shah.ecommerce.exception.CustomerNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;

@RestControllerAdvice
public class GLobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<String> handle(CustomerNotFoundException exp){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exp.getMsg());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException exp){
        var errors = new HashMap<String,String>();
                exp.getBindingResult().getAllErrors()
                .forEach(error -> {
                    String fieldName = ((FieldError)error).getField();
                    String msg = error.getDefaultMessage();
                    errors.put(fieldName,msg);
                });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(errors));
    }

}
