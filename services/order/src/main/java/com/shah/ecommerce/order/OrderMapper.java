package com.shah.ecommerce.order;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class OrderMapper {
    public Order toOrder(OrderRequest request, BigDecimal totalAmt, String generatedReference) {
        return Order.builder()
                .id(request.id())
                .customerId(request.customerId())
                .totalAmount(totalAmt)
                .reference(generatedReference)
                .paymentMethod(request.paymentMethod())
                .build();
    }

    public OrderResponse fromOrder(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getReference(),
                order.getTotalAmount(),
                order.getPaymentMethod(),
                order.getCustomerId()
        );
    }
}
