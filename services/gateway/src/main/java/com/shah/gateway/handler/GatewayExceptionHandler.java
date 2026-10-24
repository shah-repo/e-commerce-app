package com.shah.gateway.handler;

import io.netty.channel.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final ErrorResponseWriter errorResponseWriter;

    public GatewayExceptionHandler(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        var serviceName = resolveServiceName(exchange);

        if (ex instanceof NotFoundException || isConnectionFailure(ex)) {
            log.warn("{} is unavailable: {}", serviceName, ex.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                    String.format("%s is currently unavailable. Please try again later", serviceName));
        }

        if (isTimeout(ex)) {
            log.warn("{} timed out: {}", serviceName, ex.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.GATEWAY_TIMEOUT,
                    String.format("%s took too long to respond. Please try again later", serviceName));
        }

        if (ex instanceof ResponseStatusException responseStatusException) {
            var status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return errorResponseWriter.write(exchange, status, clientErrorMessage(exchange, status));
            }
        }

        log.error("Unexpected error while routing request to {}", serviceName, ex);
        return errorResponseWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong while processing your request. Please try again later");
    }

    private String clientErrorMessage(ServerWebExchange exchange, HttpStatus status) {
        var request = exchange.getRequest();
        return switch (status) {
            case NOT_FOUND -> String.format("The requested resource %s was not found", request.getPath().value());
            case METHOD_NOT_ALLOWED -> String.format("The %s method is not supported for %s", request.getMethod(), request.getPath().value());
            default -> String.format("The request could not be processed: %s", status.getReasonPhrase());
        };
    }

    private boolean isConnectionFailure(Throwable ex) {
        var cause = ex;
        while (cause != null) {
            if (cause instanceof ConnectException
                    || cause instanceof ConnectTimeoutException
                    || cause instanceof UnknownHostException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isTimeout(Throwable ex) {
        if (ex instanceof ResponseStatusException responseStatusException
                && responseStatusException.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return true;
        }
        var cause = ex;
        while (cause != null) {
            if (cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String resolveServiceName(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null || route.getUri().getHost() == null) {
            return "The requested service";
        }
        var name = route.getUri().getHost().replace("-", " ");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
