package com.shah.ecommerce.order;

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
import com.shah.ecommerce.product.PurchaseResponse;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final String ORDER_REF_PREFIX = "ORD-";
    private static final int INITIAL_ORDER_NUMBER = 1000;

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderMapper mapper;
    private final OrderLineService orderLineService;
    private final OrderProducer orderProducer;
    private final PaymentClient paymentClient;

    @Transactional(rollbackFor = Throwable.class)
    public synchronized Integer createOrder(@Valid OrderRequest request) {
        CustomerResponse customer = fetchCustomerOrThrow(request.customerId());

        boolean productsPurchased = false;
        Integer paymentId = null;

        try {
            List<PurchaseResponse> purchasedProducts = this.productClient.purchaseProducts(request.products());
            productsPurchased = true;

            BigDecimal totalAmt = purchasedProducts.stream()
                    .map(product -> product.price().multiply(BigDecimal.valueOf(product.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String generatedReference = generateNextOrderReference();
            Order order = this.orderRepository.save(mapper.toOrder(request, generatedReference, totalAmt));

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

            paymentId = executePaymentOrThrow(
                    new PaymentRequest(
                            totalAmt,
                            order.getId(),
                            order.getReference(),
                            request.paymentMethod(),
                            customer
                    )
            );

            orderProducer.sendOrderConfirmation(
                    new OrderConfirmation(
                            order.getReference(),
                            totalAmt,
                            request.paymentMethod(),
                            customer,
                            purchasedProducts
                    )
            );

            return order.getId();
        } catch (Throwable ex) {
            if (paymentId != null) {
                try {
                    paymentClient.cancelOrderPayment(paymentId);
                } catch (Exception cancelEx) {
                    log.warn("Failed to compensate payment {}: {}", paymentId, cancelEx.getMessage());
                }
            }
            if (productsPurchased) {
                productClient.releaseProducts(request.products());
            }
            throw ex;
        }
    }

    private String generateNextOrderReference() {
        List<String> existingReferences = orderRepository.findAllOrderReferencesForUpdate();
        int maxNumber = INITIAL_ORDER_NUMBER;
        if (existingReferences != null) {
            for (String ref : existingReferences) {
                if (ref != null && ref.startsWith(ORDER_REF_PREFIX)) {
                    String numericPart = ref.substring(ORDER_REF_PREFIX.length());
                    try {
                        int parsed = Integer.parseInt(numericPart);
                        if (parsed > maxNumber) {
                            maxNumber = parsed;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return ORDER_REF_PREFIX + (maxNumber + 1);
    }

    private CustomerResponse fetchCustomerOrThrow(String customerId) {
        try {
            return this.customerClient.findCustomerById(customerId)
                    .orElseThrow(() -> new BusinessException(
                            "Cannot create order: No customer exists with ID '" + customerId + "'."
                    ));
        } catch (FeignException.NotFound ex) {
            throw new BusinessException(
                    "Cannot create order: No customer exists with ID '" + customerId + "'."
            );
        } catch (RetryableException ex) {
            throw new ServiceUnavailableException(
                    "Customer Service is currently unavailable. Unable to verify customer details for your order. Please try again later."
            );
        } catch (FeignException ex) {
            if (ex.status() >= 500 || ex.status() <= 0) {
                throw new ServiceUnavailableException(
                        "Customer Service is currently unavailable. Unable to verify customer details for your order. Please try again later."
                );
            }
            throw new BusinessException(
                    "Unable to verify customer with ID '" + customerId + "'."
            );
        }
    }

    private Integer executePaymentOrThrow(PaymentRequest paymentRequest) {
        try {
            return paymentClient.requestOrderPayment(paymentRequest);
        } catch (RetryableException ex) {
            throw new ServiceUnavailableException(
                    "Payment Service is currently unavailable. Your order could not be completed and all changes have been rolled back. Please try again later."
            );
        } catch (FeignException ex) {
            if (ex.status() >= 500 || ex.status() <= 0) {
                throw new ServiceUnavailableException(
                        "Payment Service is currently unavailable. Your order could not be completed and all changes have been rolled back. Please try again later."
                );
            }
            throw new BusinessException(
                    "Payment processing failed for your order. All database changes have been rolled back."
            );
        }
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAll()
                .stream()
                .map(mapper::fromOrder)
                .toList();
    }

    public OrderResponse findById(Integer orderId) {
        return orderRepository.findById(orderId)
                .map(mapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException("No order found with the given ID: " + orderId));
    }
}
