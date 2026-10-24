package com.shah.ecommerce.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteServiceErrorHandlerTests {

    private final RemoteServiceErrorHandler handler = new RemoteServiceErrorHandler(new ObjectMapper());

    @Test
    void shouldReturnResultWhenRemoteCallSucceeds() {
        assertThat(handler.execute("Customer service", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void shouldTranslateUnavailableServiceIntoReadableError() {
        assertThatThrownBy(() -> handler.execute("Customer service", () -> {
            throw feignException(503, "{\"message\":\"Customer service is currently unavailable. Please try again later\"}");
        }))
                .isInstanceOfSatisfying(RemoteServiceException.class, exp -> {
                    assertThat(exp.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exp.getMsg()).isEqualTo("Customer service is currently unavailable, so your request could not be completed. Please try again later");
                });
    }

    @Test
    void shouldTranslateConnectionFailureIntoReadableError() {
        assertThatThrownBy(() -> handler.execute("Product service", () -> {
            throw new ResourceAccessException("Connection refused");
        }))
                .isInstanceOfSatisfying(RemoteServiceException.class, exp -> {
                    assertThat(exp.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exp.getMsg()).startsWith("Product service is currently unavailable");
                });
    }

    @Test
    void shouldExposeDownstreamBusinessErrorMessage() {
        assertThatThrownBy(() -> handler.execute("Product service", () -> {
            throw HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                    "Insufficient stock quantity for product with ID:: 1".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }))
                .isInstanceOfSatisfying(BusinessException.class, exp ->
                        assertThat(exp.getMsg()).isEqualTo("Insufficient stock quantity for product with ID:: 1"));
    }

    @Test
    void shouldJoinDownstreamValidationErrors() {
        assertThatThrownBy(() -> handler.execute("Payment service", () -> {
            throw feignException(400, "{\"errors\":{\"amount\":\"Amount is required\"}}");
        }))
                .isInstanceOfSatisfying(BusinessException.class, exp ->
                        assertThat(exp.getMsg()).isEqualTo("Amount is required"));
    }

    @Test
    void shouldTranslateAuthorizationFailure() {
        assertThatThrownBy(() -> handler.execute("Payment service", () -> {
            throw feignException(403, "");
        }))
                .isInstanceOfSatisfying(RemoteServiceException.class, exp -> {
                    assertThat(exp.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exp.getMsg()).isEqualTo("Order service is not authorized to communicate with Payment service. Please contact support");
                });
    }

    private FeignException feignException(int status, String body) {
        var request = Request.create(Request.HttpMethod.POST, "http://localhost:8222/api/v1/test", Map.of(), null, StandardCharsets.UTF_8, null);
        var response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("TestClient#call()", response);
    }
}
