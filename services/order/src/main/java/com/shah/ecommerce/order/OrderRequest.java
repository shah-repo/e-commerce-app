package com.shah.ecommerce.order;

import com.fasterxml.jackson.annotation.JsonAlias;
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
        @JsonAlias({"amount", "ammount"})
        @Positive(message = "Order amount should be positive")
        BigDecimal ammount,
        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,
        @NotBlank(message = "Customer ID is required")
        String customerId,
        @NotEmpty(message = "You should purchase at least one product")
        @Valid
        List<PurchaseRequest> products
) {
}
