package com.shah.ecommerce.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController
{
    private final PaymentService paymentService;
    @PostMapping
    public ResponseEntity<Integer> makePayment(
            @RequestBody @Valid PaymentRequest paymentRequest
    ){
        return ResponseEntity.ok(paymentService.createPayment(paymentRequest));
    }
}
