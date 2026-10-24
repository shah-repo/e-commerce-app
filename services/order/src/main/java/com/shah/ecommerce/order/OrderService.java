package com.shah.ecommerce.order;

import com.shah.ecommerce.customer.CustomerClient;
import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.exception.BusinessException;
import com.shah.ecommerce.exception.RemoteServiceErrorHandler;
import com.shah.ecommerce.kafka.OrderConfirmation;
import com.shah.ecommerce.kafka.OrderProducer;
import com.shah.ecommerce.orderline.OrderLineRequest;
import com.shah.ecommerce.orderline.OrderLineService;
import com.shah.ecommerce.payment.PaymentClient;
import com.shah.ecommerce.payment.PaymentRequest;
import com.shah.ecommerce.product.ProductClient;
import com.shah.ecommerce.product.PurchaseRequest;
import feign.FeignException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final String CUSTOMER_SERVICE = "Customer service";
    private static final String PRODUCT_SERVICE = "Product service";
    private static final String PAYMENT_SERVICE = "Payment service";
    private static final String ORDER_REFERENCE_PREFIX = "ORD-";

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderMapper mapper;
    private final OrderLineService orderLineService;
    private final OrderProducer orderProducer;
    private final PaymentClient paymentClient;
    private final RemoteServiceErrorHandler remoteServiceErrorHandler;

    @Transactional(rollbackFor = Exception.class)
    public Integer createOrder(@Valid OrderRequest request) {
//        check the customer -> Openfeign
        var customer = findCustomer(request.customerId());

        // purchase the product | (RestTemplate)
        var purchasedProducts = remoteServiceErrorHandler.execute(
                PRODUCT_SERVICE,
                () -> this.productClient.purchaseProducts(request.products())
        );
        rollbackPurchaseIfOrderFails(request.products());

        var totalAmt = purchasedProducts.stream().map((product)-> product.price().multiply(BigDecimal.valueOf(product.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        var reference = generateOrderReference();
        // persist the order
        var order = this.orderRepository.save(mapper.toOrder(request, reference, totalAmt));

        // persist orderline
        for (PurchaseRequest purchaseRequest: request.products()){
            this.orderLineService.saveOrderLine(
                    new OrderLineRequest(
                            null,
                            order.getId(),
                            purchaseRequest.productId(),
                            purchaseRequest.quantity()
                    )
            );
        }
        this.orderRepository.flush();

        // start payment process
        remoteServiceErrorHandler.execute(
                PAYMENT_SERVICE,
                () -> paymentClient.requestOrderPayment(
                        new PaymentRequest(
                                totalAmt,
                                order.getId(),
                                reference,
                                request.paymentMethod(),
                                customer
                        )
                )
        );

        // send the order confirmation -->  notification-ms (kafka), only once the order is committed
        sendOrderConfirmationAfterCommit(
                new OrderConfirmation(
                        reference,
                        totalAmt,
                        request.paymentMethod(),
                        customer,
                        purchasedProducts
                )
        );

        return order.getId();
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAll()
                .stream().map(mapper::fromOrder)
                .toList();
    }

    public OrderResponse findById(Integer orderId) {
        return orderRepository.findById(orderId)
                .map(mapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException(String.format("No oder found with the given ID: %d", orderId)));
    }

    private CustomerResponse findCustomer(String customerId) {
        return remoteServiceErrorHandler.execute(CUSTOMER_SERVICE, () -> {
                    try {
                        return this.customerClient.findCustomerById(customerId);
                    } catch (FeignException.NotFound exp) {
                        return Optional.<CustomerResponse>empty();
                    }
                })
                .orElseThrow(()-> new BusinessException(String.format("Can't create order:: No Customer exist with the provided customer id:: %s", customerId)));
    }

    private String generateOrderReference() {
        return ORDER_REFERENCE_PREFIX + orderRepository.nextReferenceNumber();
    }

    private void rollbackPurchaseIfOrderFails(List<PurchaseRequest> products) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    remoteServiceErrorHandler.execute(PRODUCT_SERVICE, () -> {
                        productClient.rollbackPurchase(products);
                        return null;
                    });
                    log.info("Order creation failed, purchased products stock has been restored");
                } catch (RuntimeException exp) {
                    log.error("Order creation failed and the purchased products stock could not be restored, manual action required for products <{}>", products, exp);
                }
            }
        });
    }

    private void sendOrderConfirmationAfterCommit(OrderConfirmation orderConfirmation) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    orderProducer.sendOrderConfirmation(orderConfirmation);
                } catch (RuntimeException exp) {
                    log.error("Order <{}> was created but the order confirmation could not be sent", orderConfirmation.orderReference(), exp);
                }
            }
        });
    }
}
