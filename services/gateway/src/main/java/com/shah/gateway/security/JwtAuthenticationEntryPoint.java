package com.shah.gateway.security;

import com.shah.gateway.handler.ErrorResponseWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, resolveMessage(ex));
    }

    private String resolveMessage(AuthenticationException ex) {
        if (!(ex instanceof OAuth2AuthenticationException)) {
            return "You need to login to access this resource. Please provide a valid token in the 'Authorization: Bearer <token>' header";
        }
        var message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("expired")) {
            return "Your session has expired. Please login again to get a new token";
        }
        if (message.contains("malformed")) {
            return "The Authorization header is malformed. Expected format: 'Authorization: Bearer <token>'";
        }
        return "The provided token is invalid. Please login again to get a new token";
    }
}
