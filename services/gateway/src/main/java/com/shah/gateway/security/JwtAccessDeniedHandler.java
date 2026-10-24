package com.shah.gateway.security;

import com.shah.gateway.handler.ErrorResponseWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    public JwtAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        var request = exchange.getRequest();
        return errorResponseWriter.write(exchange, HttpStatus.FORBIDDEN, String.format(
                "You don't have permission to access %s %s",
                request.getMethod(),
                request.getPath().value()
        ));
    }
}
