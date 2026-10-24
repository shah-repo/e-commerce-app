package com.shah.ecommerce.order;

import com.shah.ecommerce.product.PurchaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        Integer id,
        @Positive(message = "Order amount should be positive")
        BigDecimal ammount,
        @NotNull(message = "Payment method should be precised")
        PaymentMethod paymentMethod,
        @NotNull(message = "Customer ID is required")
        @NotEmpty(message = "Customer ID is required")
        @NotBlank(message = "Customer ID is required")
        String customerId,
        @NotEmpty(message = "You should at least purchase one product")
        @Valid
        List<PurchaseRequest> products
) {
}
