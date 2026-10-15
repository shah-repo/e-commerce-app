package com.shah.gateway.handler;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

@Component
@Order(-2)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred while processing the request.";
        String path = exchange.getRequest().getPath().value();
        String serviceName = resolveServiceName(path);

        if (ex instanceof NotFoundException || isConnectionException(ex)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = String.format("%s is currently unavailable. Please try again later.", serviceName);
        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = String.format("%s timed out while processing the request. Please try again later.", serviceName);
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.BAD_GATEWAY || status == HttpStatus.GATEWAY_TIMEOUT) {
                message = String.format("%s is currently unavailable. Please try again later.", serviceName);
            } else if (status == HttpStatus.NOT_FOUND) {
                message = String.format("The requested resource '%s' was not found.", path);
            } else if (rse.getReason() != null && !rse.getReason().isBlank()) {
                message = rse.getReason();
            }
        } else if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message = ex.getMessage();
        }

        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private boolean isConnectionException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException ||
                    (current.getMessage() != null && (current.getMessage().contains("Connection refused") || current.getMessage().contains("Unable to find instance")))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveServiceName(String path) {
        if (path == null) {
            return "Downstream service";
        }
        if (path.startsWith("/api/v1/customers")) {
            return "Customer service";
        }
        if (path.startsWith("/api/v1/products")) {
            return "Product service";
        }
        if (path.startsWith("/api/v1/orders") || path.startsWith("/api/v1/order-lines")) {
            return "Order service";
        }
        if (path.startsWith("/api/v1/payments")) {
            return "Payment service";
        }
        if (path.startsWith("/api/v1/auth") || path.startsWith("/api/v1/login") || path.startsWith("/auth") || path.startsWith("/login")) {
            return "Authentication service";
        }
        return "Downstream service";
    }
}