package com.shah.ecommerce.orderline;

import com.shah.ecommerce.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderLineMapper {
    public OrderLine toOrderLine(OrderLineRequest request) {
        return OrderLine.builder()
                .id(request.id())
                .productId(request.productId())
                .quantity(request.quantity())
                .order(Order.builder().id(request.orderId()).build())
                .build();
    }

    public OrderlineResponse toOrderLineResponse(OrderLine orderLine) {
        return new OrderlineResponse(orderLine.getId(), orderLine.getQuantity());
    }
}
