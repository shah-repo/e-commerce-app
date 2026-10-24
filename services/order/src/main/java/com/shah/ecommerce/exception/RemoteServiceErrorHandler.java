package com.shah.ecommerce.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemoteServiceErrorHandler {

    private final ObjectMapper objectMapper;

    public <T> T execute(String serviceName, Supplier<T> remoteCall) {
        try {
            return remoteCall.get();
        } catch (FeignException exp) {
            throw translate(serviceName, exp.status(), exp.contentUTF8(), exp);
        } catch (HttpStatusCodeException exp) {
            throw translate(serviceName, exp.getStatusCode().value(), exp.getResponseBodyAsString(), exp);
        } catch (ResourceAccessException exp) {
            throw translate(serviceName, -1, null, exp);
        }
    }

    private RuntimeException translate(String serviceName, int status, String body, Exception exp) {
        log.warn("Call to {} failed with status <{}>: {}", serviceName, status, exp.getMessage());

        if (status <= 0
                || status == HttpStatus.BAD_GATEWAY.value()
                || status == HttpStatus.SERVICE_UNAVAILABLE.value()
                || status == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return new RemoteServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    String.format("%s is currently unavailable, so your request could not be completed. Please try again later", serviceName)
            );
        }

        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new RemoteServiceException(
                    HttpStatus.BAD_GATEWAY,
                    String.format("Order service is not authorized to communicate with %s. Please contact support", serviceName)
            );
        }

        if (status >= 400 && status < 500) {
            return new BusinessException(extractMessage(body)
                    .orElse(String.format("%s rejected the request", serviceName)));
        }

        return new RemoteServiceException(
                HttpStatus.BAD_GATEWAY,
                String.format("%s failed to process the request. Please try again later", serviceName)
        );
    }

    private Optional<String> extractMessage(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        var trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return Optional.of(trimmed);
        }
        try {
            JsonNode json = objectMapper.readTree(trimmed);
            if (json.hasNonNull("message")) {
                return Optional.of(json.get("message").asText());
            }
            if (json.has("errors") && json.get("errors").isObject()) {
                var messages = new ArrayList<String>();
                json.get("errors").fields().forEachRemaining(entry -> messages.add(entry.getValue().asText()));
                return Optional.of(String.join(", ", messages));
            }
        } catch (Exception ignored) {
        }
        return Optional.of(trimmed);
    }
}
