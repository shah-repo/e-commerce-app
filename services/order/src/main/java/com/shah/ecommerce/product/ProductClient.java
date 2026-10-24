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
    @Value("${application.config.product-url}")
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
                throw new BusinessException("Unable to purchase products: received status " + responseEntity.getStatusCode());
            }

            return responseEntity.getBody();
        } catch (ResourceAccessException ex) {
            throw new ServiceUnavailableException(
                    "Product service is currently unavailable. Unable to purchase products for order creation. Please ensure product-service is running and try again."
            );
        } catch (HttpServerErrorException ex) {
            throw new ServiceUnavailableException(
                    "Product service is currently unavailable. Unable to purchase products for order creation. Please ensure product-service is running and try again."
            );
        } catch (HttpClientErrorException ex) {
            String message = extractReadableMessage(ex.getResponseBodyAsString(), "Unable to purchase requested products. Please check product IDs and available stock.");
            throw new BusinessException(message);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException(
                    "Failed to communicate with Product service while processing order. Please try again later."
            );
        }
    }

    public void rollbackPurchase(List<PurchaseRequest> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, APPLICATION_JSON_VALUE);
        HttpEntity<List<PurchaseRequest>> requestEntity = new HttpEntity<>(requestBody, headers);
        try {
            restTemplate.exchange(
                    productUrl + "/rollback-purchase",
                    POST,
                    requestEntity,
                    Void.class
            );
        } catch (Exception ex) {
            log.error("Failed to rollback product stock purchase during order compensation: {}", ex.getMessage());
        }
    }

    private String extractReadableMessage(String rawBody, String fallback) {
        if (rawBody == null || rawBody.isBlank()) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            return rawBody;
        }
        return fallback;
    }
}
