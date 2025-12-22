package com.shah.ecommerce.payment;

import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.order.PaymentMethod;

import java.math.BigDecimal;

public record PaymentRequest(
        BigDecimal amount,
        Integer orderId,
        String orderReference,
        PaymentMethod paymentMethod,
        CustomerResponse customer
) {
}
