package com.shah.ecommerce.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shah.ecommerce.customer.CustomerClient;
import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.exception.BusinessException;
import com.shah.ecommerce.exception.RemoteServiceErrorHandler;
import com.shah.ecommerce.exception.RemoteServiceException;
import com.shah.ecommerce.kafka.OrderConfirmation;
import com.shah.ecommerce.kafka.OrderProducer;
import com.shah.ecommerce.orderline.OrderLineService;
import com.shah.ecommerce.payment.PaymentClient;
import com.shah.ecommerce.payment.PaymentRequest;
import com.shah.ecommerce.product.ProductClient;
import com.shah.ecommerce.product.PurchaseRequest;
import com.shah.ecommerce.product.PurchaseResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTests {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CustomerClient customerClient = mock(CustomerClient.class);
    private final ProductClient productClient = mock(ProductClient.class);
    private final OrderLineService orderLineService = mock(OrderLineService.class);
    private final OrderProducer orderProducer = mock(OrderProducer.class);
    private final PaymentClient paymentClient = mock(PaymentClient.class);

    private final OrderService orderService = new OrderService(
            orderRepository,
            customerClient,
            productClient,
            new OrderMapper(),
            orderLineService,
            orderProducer,
            paymentClient,
            new RemoteServiceErrorHandler(new ObjectMapper())
    );

    private final List<PurchaseRequest> products = List.of(new PurchaseRequest(1, 2));
    private final OrderRequest request = new OrderRequest(null, null, PaymentMethod.VISA, "customer-1", products);
    private final CustomerResponse customer = new CustomerResponse("customer-1", "John", "Doe", "john@doe.com");

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.of(customer));
        when(productClient.purchaseProducts(products)).thenReturn(List.of(
                new PurchaseResponse(1, "Laptop", "High performance laptop", BigDecimal.valueOf(100), 2)
        ));
        when(orderRepository.nextReferenceNumber()).thenReturn(1001L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1);
            return order;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void shouldAutoGenerateReferenceAndSendConfirmationAfterCommit() {
        var orderId = orderService.createOrder(request);

        assertThat(orderId).isEqualTo(1);
        var orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getReference()).isEqualTo("ORD-1001");
        assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("200");

        var paymentCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient).requestOrderPayment(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().orderReference()).isEqualTo("ORD-1001");
        verifyNoInteractions(orderProducer);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        var confirmationCaptor = ArgumentCaptor.forClass(OrderConfirmation.class);
        verify(orderProducer).sendOrderConfirmation(confirmationCaptor.capture());
        assertThat(confirmationCaptor.getValue().orderReference()).isEqualTo("ORD-1001");
        verify(productClient, never()).rollbackPurchase(any());
    }

    @Test
    void shouldRestoreProductStockWhenOrderCreationFails() {
        when(paymentClient.requestOrderPayment(any())).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOfSatisfying(RemoteServiceException.class, exp -> {
                    assertThat(exp.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exp.getMsg()).startsWith("Payment service is currently unavailable");
                });

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(productClient).rollbackPurchase(products);
        verifyNoInteractions(orderProducer);
    }

    @Test
    void shouldNotRestoreProductStockWhenPurchaseFails() {
        when(productClient.purchaseProducts(products)).thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> orderService.createOrder(request)).isInstanceOf(RemoteServiceException.class);

        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(productClient, never()).rollbackPurchase(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnknownCustomer() {
        when(customerClient.findCustomerById("customer-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOfSatisfying(BusinessException.class, exp ->
                        assertThat(exp.getMsg()).contains("No Customer exist with the provided customer id:: customer-1"));
        verifyNoInteractions(productClient);
    }

    private void completeTransaction(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
