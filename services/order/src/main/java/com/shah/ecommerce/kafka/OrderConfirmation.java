package com.shah.ecommerce.kafka;

import com.shah.ecommerce.customer.CustomerResponse;
import com.shah.ecommerce.order.PaymentMethod;
import com.shah.ecommerce.product.PurchaseResponse;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmation(
        String orderReference,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        CustomerResponse customer,
        List<PurchaseResponse> products
) {
}
