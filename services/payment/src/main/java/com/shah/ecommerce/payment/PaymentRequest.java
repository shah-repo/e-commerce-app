package com.shah.ecommerce.payment;

import java.math.BigDecimal;

public record PaymentRequest(
        Integer id,
        BigDecimal amount,
        Integer orderId,
        String orderReference,
        PaymentMethod paymentMethod,
        Customer customer
) {
}
