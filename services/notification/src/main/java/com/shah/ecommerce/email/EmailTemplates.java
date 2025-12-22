package com.shah.ecommerce.email;

import lombok.Getter;

public enum EmailTemplates {
    PAYMENT_CONFIRMATION("payment-success.html", "Payment Successfully processed"),
    ORDER_CONFIRMATION("order-success.html", "Order Confirmation");

    @Getter
    private final String templateName;
    @Getter
    private final String templateSubject;

    EmailTemplates(String templateName, String templateSubject) {
        this.templateName = templateName;
        this.templateSubject = templateSubject;
    }
}
