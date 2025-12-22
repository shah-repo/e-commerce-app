package com.shah.ecommerce.order;

import java.math.BigDecimal;

public record OrderResponse(
        Integer orderId,
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        String customerId
) {
}
