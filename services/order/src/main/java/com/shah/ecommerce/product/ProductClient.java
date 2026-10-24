package com.shah.ecommerce.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shah.ecommerce.exception.BusinessException;
import com.shah.ecommerce.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductClient {
    @Value("${application.config.product-url:http://localhost:8222/api/v1/products}")
    private String productUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public List<PurchaseResponse> purchaseProducts(List<PurchaseRequest> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON_VALUE);

        HttpEntity<List<PurchaseRequest>> requestEntity = new HttpEntity<>(requestBody, headers);
        ParameterizedTypeReference<List<PurchaseResponse>> responseType = new ParameterizedTypeReference<>() {};

        try {
            ResponseEntity<List<PurchaseResponse>> responseEntity = restTemplate.exchange(
                    productUrl + "/purchase",
                    POST,
                    requestEntity,
                    responseType
            );

            if (responseEntity.getStatusCode().isError()) {
                throw new BusinessException("Unable to purchase products: received error status " + responseEntity.getStatusCode());
            }

            return responseEntity.getBody();
        } catch (ResourceAccessException ex) {
            throw new ServiceUnavailableException(
                    "Product Service is currently unavailable. Unable to check or reserve products for your order. Please try again later."
            );
        } catch (HttpServerErrorException ex) {
            throw new ServiceUnavailableException(
                    "Product Service is currently unavailable. Unable to check or reserve products for your order. Please try again later."
            );
        } catch (HttpClientErrorException ex) {
            throw new BusinessException(extractReadableMessage(ex.getResponseBodyAsString(), "Unable to purchase the requested products."));
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException(
                    "Product Service is currently unavailable. Unable to check or reserve products for your order. Please try again later."
            );
        }
    }

    public void releaseProducts(List<PurchaseRequest> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON_VALUE);
        HttpEntity<List<PurchaseRequest>> requestEntity = new HttpEntity<>(requestBody, headers);
        try {
            restTemplate.exchange(
                    productUrl + "/release",
                    POST,
                    requestEntity,
                    Void.class
            );
        } catch (Exception ex) {
            log.warn("Failed to compensate product stock release: {}", ex.getMessage());
        }
    }

    private String extractReadableMessage(String rawBody, String fallback) {
        if (rawBody == null || rawBody.isBlank()) {
            return fallback;
        }
        try {
            JsonNode json = objectMapper.readTree(rawBody);
            if (json.hasNonNull("message") && !json.get("message").asText().isBlank()) {
                return json.get("message").asText();
            }
            if (json.hasNonNull("errors")) {
                return json.get("errors").toString();
            }
        } catch (Exception ignored) {
        }
        return rawBody;
    }
}
