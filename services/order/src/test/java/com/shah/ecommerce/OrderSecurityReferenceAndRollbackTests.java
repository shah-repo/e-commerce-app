package com.shah.ecommerce;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shah.ecommerce.config.SecurityConfig;
import com.shah.ecommerce.config.ServiceTokenProvider;
import com.shah.ecommerce.customer.CustomerClient;
import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.exception.ServiceUnavailableException;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
import com.shah.ecommerce.kafka.OrderConfirmation;
import com.shah.ecommerce.kafka.OrderProducer;
import com.shah.ecommerce.order.Order;
import com.shah.ecommerce.order.OrderController;
import com.shah.ecommerce.order.OrderMapper;
import com.shah.ecommerce.order.OrderRepository;
import com.shah.ecommerce.order.OrderService;
import com.shah.ecommerce.orderline.OrderLine;
import com.shah.ecommerce.orderline.OrderLineController;
import com.shah.ecommerce.orderline.OrderLineMapper;
import com.shah.ecommerce.orderline.OrderLineRepository;
import com.shah.ecommerce.orderline.OrderLineService;
import com.shah.ecommerce.payment.PaymentClient;
import com.shah.ecommerce.payment.PaymentRequest;
import com.shah.ecommerce.product.ProductClient;
import com.shah.ecommerce.product.PurchaseRequest;
import com.shah.ecommerce.product.PurchaseResponse;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.KafkaException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {OrderController.class, OrderLineController.class})
@Import({
        OrderService.class,
        OrderMapper.class,
        OrderLineService.class,
        OrderLineMapper.class,
        SecurityConfig.class,
        ServiceTokenProvider.class,
        GLobalExceptionHandler.class
})
class OrderSecurityReferenceAndRollbackTests {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private OrderLineRepository orderLineRepository;

    @MockitoBean
    private CustomerClient customerClient;

    @MockitoBean
    private ProductClient productClient;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private OrderProducer orderProducer;

    private String adminToken;
    private String userToken;
    private final List<Order> persistedOrders = new ArrayList<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);

    @BeforeEach
    void setUp() {
        persistedOrders.clear();
        idSequence.set(1);

        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.adminToken = generateToken(encoder, "admin", "admin_role");
        this.userToken = generateToken(encoder, "user", "user_role");

        when(orderRepository.findAllOrdReferencesForUpdate()).thenAnswer(inv -> new ArrayList<>(persistedOrders));
        when(orderRepository.findAll()).thenAnswer(inv -> new ArrayList<>(persistedOrders));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(idSequence.getAndIncrement());
            persistedOrders.add(order);
            return order;
        });
        when(orderLineRepository.save(any(OrderLine.class))).thenAnswer(inv -> {
            OrderLine line = inv.getArgument(0);
            line.setId(100);
            return line;
        });
    }

    private String generateToken(JwtEncoder encoder, String username, String role) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", role)
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Test
    void sequentialOrderReferencesAreGeneratedWithoutReferenceInRequestBody() throws Exception {
        CustomerResponse customer = new CustomerResponse("cust-1", "John", "Doe", "john@example.com");
        when(customerClient.findCustomerById("cust-1")).thenReturn(Optional.of(customer));
        when(productClient.purchaseProducts(any())).thenReturn(List.of(
                new PurchaseResponse(1, "Laptop", "Gaming laptop", new BigDecimal("1000.00"), 2)
        ));
        when(paymentClient.requestOrderPayment(any(PaymentRequest.class))).thenReturn(50);

        String requestBodyWithoutReference = """
                {
                  "paymentMethod": "CREDIT_CARD",
                  "customerId": "cust-1",
                  "products": [
                    { "productId": 1, "quantity": 2 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyWithoutReference))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyWithoutReference))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));

        assertThat(persistedOrders).hasSize(2);
        assertThat(persistedOrders.get(0).getReference()).isEqualTo("ORD-1001");
        assertThat(persistedOrders.get(1).getReference()).isEqualTo("ORD-1002");

        ArgumentCaptor<PaymentRequest> paymentCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient, times(2)).requestOrderPayment(paymentCaptor.capture());
        assertThat(paymentCaptor.getAllValues().get(0).orderReference()).isEqualTo("ORD-1001");
        assertThat(paymentCaptor.getAllValues().get(1).orderReference()).isEqualTo("ORD-1002");

        ArgumentCaptor<OrderConfirmation> kafkaCaptor = ArgumentCaptor.forClass(OrderConfirmation.class);
        verify(orderProducer, times(2)).sendOrderConfirmation(kafkaCaptor.capture());
        assertThat(kafkaCaptor.getAllValues().get(0).orderReference()).isEqualTo("ORD-1001");
        assertThat(kafkaCaptor.getAllValues().get(1).orderReference()).isEqualTo("ORD-1002");
    }

    @Test
    void roleAuthorizationBlocksUserOnGetOrdersAndOrderLinesWhileAllowingAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/order-lines/orders/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/order-lines/orders/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void paymentServiceDownTriggersStockRollbackAndReturnsReadable503() throws Exception {
        CustomerResponse customer = new CustomerResponse("cust-1", "John", "Doe", "john@example.com");
        when(customerClient.findCustomerById("cust-1")).thenReturn(Optional.of(customer));
        when(productClient.purchaseProducts(any())).thenReturn(List.of(
                new PurchaseResponse(1, "Laptop", "Gaming laptop", new BigDecimal("1000.00"), 1)
        ));

        Request dummyFeignRequest = Request.create(
                Request.HttpMethod.POST,
                "http://localhost:8222/api/v1/payments",
                Collections.emptyMap(),
                new byte[0],
                StandardCharsets.UTF_8,
                null
        );
        when(paymentClient.requestOrderPayment(any(PaymentRequest.class))).thenThrow(
                new RetryableException(
                        503,
                        "Connection refused",
                        Request.HttpMethod.POST,
                        new Date(),
                        dummyFeignRequest
                )
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "CREDIT_CARD",
                                  "customerId": "cust-1",
                                  "products": [{ "productId": 1, "quantity": 1 }]
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Payment service is currently unavailable. Order creation and product stock changes have been rolled back. Please ensure payment-service is running and try again."
                ));

        verify(productClient, times(1)).rollbackPurchase(List.of(new PurchaseRequest(1, 1)));
    }

    @Test
    void kafkaDownTriggersPaymentAndProductRollbacksAndReturnsReadable503() throws Exception {
        CustomerResponse customer = new CustomerResponse("cust-1", "John", "Doe", "john@example.com");
        when(customerClient.findCustomerById("cust-1")).thenReturn(Optional.of(customer));
        when(productClient.purchaseProducts(any())).thenReturn(List.of(
                new PurchaseResponse(1, "Laptop", "Gaming laptop", new BigDecimal("1000.00"), 1)
        ));
        when(paymentClient.requestOrderPayment(any(PaymentRequest.class))).thenReturn(77);
        doThrow(new KafkaException("Broker unreachable")).when(orderProducer).sendOrderConfirmation(any());

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "PAYPAL",
                                  "customerId": "cust-1",
                                  "products": [{ "productId": 1, "quantity": 1 }]
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Notification messaging service (Kafka) is currently unavailable. Order creation, payment, and product stock changes have been rolled back."
                ));

        verify(paymentClient, times(1)).cancelOrderPayment(77);
        verify(productClient, times(1)).rollbackPurchase(List.of(new PurchaseRequest(1, 1)));
    }

    @Test
    void productServiceDownReturnsReadable503WithoutAnyRollbackSideEffects() throws Exception {
        CustomerResponse customer = new CustomerResponse("cust-1", "John", "Doe", "john@example.com");
        when(customerClient.findCustomerById("cust-1")).thenReturn(Optional.of(customer));
        when(productClient.purchaseProducts(any())).thenThrow(
                new ServiceUnavailableException("Product service is currently unavailable. Unable to purchase products for order creation. Please ensure product-service is running and try again.")
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "VISA",
                                  "customerId": "cust-1",
                                  "products": [{ "productId": 1, "quantity": 1 }]
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        verify(productClient, never()).rollbackPurchase(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }
}
