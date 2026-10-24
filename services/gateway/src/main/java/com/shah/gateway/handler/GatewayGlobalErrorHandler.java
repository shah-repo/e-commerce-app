package com.shah.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Component
@Order(-2)
public class GatewayGlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayGlobalErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getPath().value();
        String serviceName = resolveServiceName(path);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred while processing your request. Please try again later.";

        if (ex instanceof NotFoundException || isConnectionOrTimeoutFailure(ex)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = serviceName + " is currently unavailable. Please ensure the service is running and try again later.";
        } else if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.BAD_GATEWAY || status == HttpStatus.GATEWAY_TIMEOUT) {
                message = serviceName + " is currently unavailable. Please ensure the service is running and try again later.";
            } else if (status == HttpStatus.NOT_FOUND) {
                message = "The requested endpoint '" + path + "' does not exist.";
            } else {
                message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
            }
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiErrorResponse errorBody = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                Instant.now().toString()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException jsonEx) {
            bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isConnectionOrTimeoutFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof TimeoutException
                    || current instanceof UnknownHostException
                    || (current.getClass().getName().contains("ConnectException"))
                    || (current.getClass().getName().contains("ReadTimeoutException"))
                    || (current.getClass().getName().contains("PrematureCloseException"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String resolveServiceName(String path) {
        if (path == null) {
            return "Requested Service";
        }
        if (path.contains("/auth")) {
            return "Authentication Service";
        }
        if (path.contains("/customers")) {
            return "Customer Service";
        }
        if (path.contains("/products")) {
            return "Product Service";
        }
        if (path.contains("/orders") || path.contains("/order-lines")) {
            return "Order Service";
        }
        if (path.contains("/payments")) {
            return "Payment Service";
        }
        return "Downstream Service";
    }
}
