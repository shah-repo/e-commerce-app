package com.shah.ecommerce.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shah.ecommerce.customer.CustomerClient;
import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.exception.BusinessException;
import com.shah.ecommerce.exception.ServiceUnavailableException;
import com.shah.ecommerce.kafka.OrderConfirmation;
import com.shah.ecommerce.kafka.OrderProducer;
import com.shah.ecommerce.orderline.OrderLineRequest;
import com.shah.ecommerce.orderline.OrderLineService;
import com.shah.ecommerce.payment.PaymentClient;
import com.shah.ecommerce.payment.PaymentRequest;
import com.shah.ecommerce.product.ProductClient;
import com.shah.ecommerce.product.PurchaseRequest;
import feign.FeignException;
import feign.RetryableException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final String ORDER_REFERENCE_PREFIX = "ORD-";
    private static final long ORDER_REFERENCE_BASE = 1000L;

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderMapper mapper;
    private final OrderLineService orderLineService;
    private final OrderProducer orderProducer;
    private final PaymentClient paymentClient;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public synchronized Integer createOrder(@Valid OrderRequest request) {
        CustomerResponse customer = fetchCustomerOrThrow(request.customerId());

        boolean productsPurchased = false;
        Integer paymentId = null;

        try {
            var purchasedProducts = this.productClient.purchaseProducts(request.products());
            productsPurchased = true;

            var totalAmt = purchasedProducts.stream()
                    .map(product -> product.price().multiply(BigDecimal.valueOf(product.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String generatedReference = generateNextOrderReference();

            var order = this.orderRepository.saveAndFlush(mapper.toOrder(request, totalAmt, generatedReference));

            for (PurchaseRequest purchaseRequest : request.products()) {
                this.orderLineService.saveOrderLine(
                        new OrderLineRequest(
                                null,
                                order.getId(),
                                purchaseRequest.productId(),
                                purchaseRequest.quantity()
                        )
                );
            }

            paymentId = requestPaymentOrThrow(
                    new PaymentRequest(
                            totalAmt,
                            order.getId(),
                            generatedReference,
                            request.paymentMethod(),
                            customer
                    )
            );

            sendOrderConfirmationOrThrow(
                    new OrderConfirmation(
                            generatedReference,
                            totalAmt,
                            request.paymentMethod(),
                            customer,
                            purchasedProducts
                    )
            );

            return order.getId();
        } catch (Exception ex) {
            log.error("Order creation failed ({}). Rolling back database entries and compensating downstream operations.", ex.getMessage());
            if (paymentId != null) {
                safelyCancelPayment(paymentId);
            }
            if (productsPurchased) {
                this.productClient.rollbackPurchase(request.products());
            }
            throw ex;
        }
    }

    private String generateNextOrderReference() {
        long maxNumber = this.orderRepository.findAllOrdReferencesForUpdate().stream()
                .map(Order::getReference)
                .filter(Objects::nonNull)
                .map(ref -> {
                    if (ref.startsWith(ORDER_REFERENCE_PREFIX)) {
                        try {
                            return Long.parseLong(ref.substring(ORDER_REFERENCE_PREFIX.length()));
                        } catch (NumberFormatException ignored) {
                            return ORDER_REFERENCE_BASE;
                        }
                    }
                    return ORDER_REFERENCE_BASE;
                })
                .max(Long::compareTo)
                .orElse(ORDER_REFERENCE_BASE);

        long nextNumber = Math.max(ORDER_REFERENCE_BASE, maxNumber) + 1;
        return ORDER_REFERENCE_PREFIX + nextNumber;
    }

    private CustomerResponse fetchCustomerOrThrow(String customerId) {
        try {
            return this.customerClient.findCustomerById(customerId)
                    .orElseThrow(() -> new BusinessException(
                            String.format("Cannot create order: No customer found with ID '%s'.", customerId)
                    ));
        } catch (FeignException.NotFound ex) {
            throw new BusinessException(
                    String.format("Cannot create order: No customer found with ID '%s'.", customerId)
            );
        } catch (RetryableException ex) {
            throw new ServiceUnavailableException(
                    "Customer service is currently unavailable. Unable to verify customer details for order creation. Please ensure customer-service is running and try again."
            );
        } catch (FeignException ex) {
            if (ex.status() >= 500 || ex.status() <= 0) {
                throw new ServiceUnavailableException(
                        "Customer service is currently unavailable. Unable to verify customer details for order creation. Please ensure customer-service is running and try again."
                );
            }
            throw new BusinessException(extractFeignMessage(
                    ex,
                    String.format("Cannot create order: Unable to validate customer with ID '%s'.", customerId)
            ));
        }
    }

    private Integer requestPaymentOrThrow(PaymentRequest paymentRequest) {
        try {
            return this.paymentClient.requestOrderPayment(paymentRequest);
        } catch (RetryableException ex) {
            throw new ServiceUnavailableException(
                    "Payment service is currently unavailable. Order creation and product stock changes have been rolled back. Please ensure payment-service is running and try again."
            );
        } catch (FeignException ex) {
            if (ex.status() >= 500 || ex.status() <= 0) {
                throw new ServiceUnavailableException(
                        "Payment service is currently unavailable. Order creation and product stock changes have been rolled back. Please ensure payment-service is running and try again."
                );
            }
            throw new BusinessException(extractFeignMessage(
                    ex,
                    "Payment processing failed for this order. Order creation and product stock changes have been rolled back."
            ));
        }
    }

    private void sendOrderConfirmationOrThrow(OrderConfirmation confirmation) {
        try {
            this.orderProducer.sendOrderConfirmation(confirmation);
        } catch (Exception ex) {
            throw new ServiceUnavailableException(
                    "Notification messaging service (Kafka) is currently unavailable. Order creation, payment, and product stock changes have been rolled back."
            );
        }
    }

    private void safelyCancelPayment(Integer paymentId) {
        try {
            this.paymentClient.cancelOrderPayment(paymentId);
        } catch (Exception ex) {
            log.error("Failed to rollback payment {} during order compensation: {}", paymentId, ex.getMessage());
        }
    }

    private String extractFeignMessage(FeignException ex, String fallback) {
        try {
            String rawBody = ex.contentUTF8();
            if (rawBody != null && !rawBody.isBlank()) {
                JsonNode node = objectMapper.readTree(rawBody);
                if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) {
                    return node.get("message").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAll()
                .stream().map(mapper::fromOrder)
                .toList();
    }

    public OrderResponse findById(Integer orderId) {
        return orderRepository.findById(orderId)
                .map(mapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("No order found with ID: %d", orderId)
                ));
    }
}
