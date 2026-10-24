package com.shah.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.shah.ecommerce.customer.CustomerClient;
import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.exception.ServiceUnavailableException;
import com.shah.ecommerce.handler.GLobalExceptionHandler;
import com.shah.ecommerce.kafka.OrderProducer;
import com.shah.ecommerce.order.*;
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
import com.shah.ecommerce.security.SecurityConfig;
import com.shah.ecommerce.security.ServiceTokenProvider;
import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OrderController.class, OrderLineController.class})
@Import({SecurityConfig.class, ServiceTokenProvider.class, GLobalExceptionHandler.class})
class OrderApplicationTests {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderLineService orderLineService;

    private String token(String role) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Instant now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .subject("tester")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(3600))
                        .claim("role", role)
                        .claim("roles", List.of(role))
                        .build()
        )).getTokenValue();
    }

    @Test
    void orderRequestShouldNotContainReferenceField() {
        List<String> componentNames = Arrays.stream(OrderRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(componentNames).doesNotContain("reference");
    }

    @Test
    void shouldEnforceExactRoleBasedAuthorizationAndHumanReadableErrors() throws Exception {
        String userToken = token("user_role");
        String adminToken = token("admin_role");

        String validCreateOrderBody = """
                {
                  "paymentMethod": "CREDIT_CARD",
                  "customerId": "cust-101",
                  "products": [
                    { "productId": 1, "quantity": 2 }
                  ]
                }
                """;

        when(orderService.createOrder(any())).thenReturn(1);
        when(orderService.findAll()).thenReturn(List.of(
                new OrderResponse(1, "ORD-1001", new BigDecimal("200.00"), PaymentMethod.CREDIT_CARD, "cust-101")
        ));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/order-lines/orders/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/orders/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/order-lines/orders/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderReference").value("ORD-1001"));

        mockMvc.perform(get("/api/v1/order-lines/orders/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        when(orderService.createOrder(any())).thenThrow(new ServiceUnavailableException(
                "Payment Service is currently unavailable. Your order could not be completed and all changes have been rolled back. Please try again later."
        ));

        mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateOrderBody))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(
                        "Payment Service is currently unavailable. Your order could not be completed and all changes have been rolled back. Please try again later."
                ));
    }

    @Test
    void shouldAutoGenerateSequentialReferencesSaveToDatabaseAndRollBackAllEntriesOnFailure() {
        List<Order> committedOrders = new ArrayList<>();
        List<Order> stagedOrders = new ArrayList<>();
        List<OrderLine> committedLines = new ArrayList<>();
        List<OrderLine> stagedLines = new ArrayList<>();
        AtomicInteger idSeq = new AtomicInteger(1);
        AtomicInteger lineIdSeq = new AtomicInteger(1);

        PlatformTransactionManager txManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() throws TransactionException {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
                stagedOrders.clear();
                stagedLines.clear();
                stagedOrders.addAll(committedOrders);
                stagedLines.addAll(committedLines);
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
                committedOrders.clear();
                committedOrders.addAll(stagedOrders);
                committedLines.clear();
                committedLines.addAll(stagedLines);
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
                stagedOrders.clear();
                stagedLines.clear();
            }
        };

        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderLineRepository orderLineRepository = mock(OrderLineRepository.class);
        CustomerClient customerClient = mock(CustomerClient.class);
        ProductClient productClient = mock(ProductClient.class);
        PaymentClient paymentClient = mock(PaymentClient.class);
        OrderProducer orderProducer = mock(OrderProducer.class);

        when(orderRepository.findAllOrderReferencesForUpdate()).thenAnswer(inv ->
                stagedOrders.stream().map(Order::getReference).toList()
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(idSeq.getAndIncrement());
            }
            stagedOrders.add(o);
            return o;
        });
        when(orderRepository.findAll()).thenAnswer(inv -> new ArrayList<>(committedOrders));

        when(orderLineRepository.save(any(OrderLine.class))).thenAnswer(inv -> {
            OrderLine line = inv.getArgument(0);
            line.setId(lineIdSeq.getAndIncrement());
            stagedLines.add(line);
            return line;
        });

        OrderLineService realOrderLineService = new OrderLineService(orderLineRepository, new OrderLineMapper());
        OrderService targetService = new OrderService(
                orderRepository,
                customerClient,
                productClient,
                new OrderMapper(),
                realOrderLineService,
                orderProducer,
                paymentClient
        );

        TransactionInterceptor txInterceptor = new TransactionInterceptor();
        txInterceptor.setTransactionManager(txManager);
        txInterceptor.setTransactionAttributeSource(
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()
        );
        txInterceptor.afterPropertiesSet();

        ProxyFactory proxyFactory = new ProxyFactory(targetService);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(txInterceptor);
        OrderService transactionalOrderService = (OrderService) proxyFactory.getProxy();

        CustomerResponse customer = new CustomerResponse("cust-1", "Shah", "Dev", "shah@test.com");
        when(customerClient.findCustomerById("cust-1")).thenReturn(Optional.of(customer));
        when(productClient.purchaseProducts(any())).thenReturn(List.of(
                new PurchaseResponse(10, "Keyboard", "Mechanical Keyboard", new BigDecimal("100.00"), 2.0)
        ));
        when(paymentClient.requestOrderPayment(any())).thenReturn(901);

        OrderRequest request = new OrderRequest(
                null,
                null,
                PaymentMethod.CREDIT_CARD,
                "cust-1",
                List.of(new PurchaseRequest(10, 2.0))
        );

        Integer order1Id = transactionalOrderService.createOrder(request);
        assertThat(order1Id).isEqualTo(1);
        assertThat(committedOrders).hasSize(1);
        assertThat(committedLines).hasSize(1);
        assertThat(committedOrders.get(0).getReference()).isEqualTo("ORD-1001");

        Request dummyFeignReq = Request.create(
                Request.HttpMethod.POST,
                "http://localhost:8222/api/v1/payments",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        when(paymentClient.requestOrderPayment(any())).thenThrow(
                new RetryableException(503, "Connection refused", Request.HttpMethod.POST, (Long) null, dummyFeignReq)
        );

        assertThatThrownBy(() -> transactionalOrderService.createOrder(request))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("Payment Service is currently unavailable");

        assertThat(committedOrders).hasSize(1);
        assertThat(committedLines).hasSize(1);
        verify(productClient, times(1)).releaseProducts(request.products());

        reset(paymentClient);
        when(paymentClient.requestOrderPayment(any())).thenReturn(902);
        doThrow(new ServiceUnavailableException("Kafka broker down"))
                .when(orderProducer).sendOrderConfirmation(any());

        assertThatThrownBy(() -> transactionalOrderService.createOrder(request))
                .isInstanceOf(ServiceUnavailableException.class);

        assertThat(committedOrders).hasSize(1);
        assertThat(committedLines).hasSize(1);
        verify(paymentClient, times(1)).cancelOrderPayment(902);
        verify(productClient, times(2)).releaseProducts(request.products());

        doNothing().when(orderProducer).sendOrderConfirmation(any());
        Integer order2Id = transactionalOrderService.createOrder(request);
        assertThat(order2Id).isNotNull();
        assertThat(committedOrders).hasSize(2);
        assertThat(committedOrders.get(0).getReference()).isEqualTo("ORD-1001");
        assertThat(committedOrders.get(1).getReference()).isEqualTo("ORD-1002");

        ArgumentCaptor<PaymentRequest> paymentCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(paymentClient, times(2)).requestOrderPayment(paymentCaptor.capture());
        assertThat(paymentCaptor.getAllValues().get(1).orderReference()).isEqualTo("ORD-1002");
    }
}
