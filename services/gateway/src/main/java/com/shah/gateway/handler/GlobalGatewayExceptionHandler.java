package com.shah.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(-2)
public class GlobalGatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalGatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalGatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();
        String serviceName = resolveServiceName(path);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String error = "Internal Server Error";
        String message = String.format("An unexpected error occurred while processing request to %s.", serviceName);

        if (ex instanceof NotFoundException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            error = "Service Unavailable";
            message = String.format("%s is currently unavailable. Please ensure the service is running and try again later.", serviceName);
        } else if (isConnectionOrTimeoutError(ex)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            error = "Service Unavailable";
            message = String.format("%s is currently unreachable or not responding. Please ensure the service is running and try again later.", serviceName);
        } else if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            status = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
            if (status == HttpStatus.SERVICE_UNAVAILABLE || status == HttpStatus.BAD_GATEWAY || status == HttpStatus.GATEWAY_TIMEOUT) {
                error = "Service Unavailable";
                message = String.format("%s is currently unavailable. Please ensure the service is running and try again later.", serviceName);
            } else if (status == HttpStatus.NOT_FOUND) {
                error = "Not Found";
                message = String.format("The requested endpoint '%s' was not found.", path);
            } else {
                error = status.getReasonPhrase();
                message = rse.getReason() != null && !rse.getReason().isBlank()
                        ? rse.getReason()
                        : String.format("Request failed with status %d (%s).", status.value(), status.getReasonPhrase());
            }
        }

        log.error("Gateway error on path '{}': {} -> {}", path, ex.getClass().getSimpleName(), ex.getMessage());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException jsonEx) {
            bytes = ("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isConnectionOrTimeoutError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof TimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current instanceof IOException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.contains("ConnectException") || className.contains("TimeoutException")) {
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
        if (path.contains("/api/v1/auth")) {
            return "Authentication service";
        }
        if (path.contains("/api/v1/customers")) {
            return "Customer service";
        }
        if (path.contains("/api/v1/products")) {
            return "Product service";
        }
        if (path.contains("/api/v1/orders") || path.contains("/api/v1/order-lines")) {
            return "Order service";
        }
        if (path.contains("/api/v1/payments")) {
            return "Payment service";
        }
        return "Downstream service";
    }
}
