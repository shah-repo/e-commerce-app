package com.shah.ecommerce.order;

import com.shah.ecommerce.product.PurchaseRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        Integer id,
        String reference,
        @Positive(message = "Order amount should be positive")
        BigDecimal ammount,
        @NotNull(message = "Payment method should be precised")
        PaymentMethod paymentMethod,
        @NotNull(message = "Payment method should be precised")
        @NotEmpty(message = "Payment method should be precised")
        @NotBlank(message = "Payment method should be precised")
        String customerId,
        @NotEmpty(message = "You should at least purchase one product")
        List<PurchaseRequest> products
) {
}
