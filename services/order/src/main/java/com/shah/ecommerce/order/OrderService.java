package com.shah.ecommerce.order;

import com.shah.ecommerce.customer.CustomerClient;
import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.exception.BusinessException;
import com.shah.ecommerce.kafka.OrderConfirmation;
import com.shah.ecommerce.kafka.OrderProducer;
import com.shah.ecommerce.orderline.OrderLineRequest;
import com.shah.ecommerce.orderline.OrderLineService;
import com.shah.ecommerce.payment.PaymentClient;
import com.shah.ecommerce.payment.PaymentRequest;
import com.shah.ecommerce.product.ProductClient;
import com.shah.ecommerce.product.PurchaseRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderMapper mapper;
    private final OrderLineService orderLineService;
    private final OrderProducer orderProducer;
    private final PaymentClient paymentClient;

    public Integer createOrder(@Valid OrderRequest request) {
//        check the customer -> Openfeign
        var customer = this.customerClient.findCustomerById(request.customerId())
                .orElseThrow(()-> new BusinessException(String.format("Can't create order:: No Customer exist with the provided customer id:: %s",request.customerId())));

        // purchase the product | (RestTemplate)
        var purchasedProducts = this.productClient.purchaseProducts(request.products());

        var totalAmt = purchasedProducts.stream().map((product)-> product.price().multiply(BigDecimal.valueOf(product.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        // persist the order
        var order = this.orderRepository.save(mapper.toOrder(request, totalAmt));

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

        // start payment process
        paymentClient.requestOrderPayment(
                new PaymentRequest(
                        totalAmt,
                        order.getId(),
                        request.reference(),
                        request.paymentMethod(),
                        customer
                )
        );


        // send the order confirmation -->  notification-ms (kafka)

        orderProducer.sendOrderConfirmation(
                new OrderConfirmation(
                        request.reference(),
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
}
