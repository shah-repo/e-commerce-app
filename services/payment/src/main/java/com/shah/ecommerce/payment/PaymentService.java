package com.shah.ecommerce.payment;

import com.shah.ecommerce.notification.KafkaNotificationProducer;
import com.shah.ecommerce.notification.PaymentNotificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final KafkaNotificationProducer producer;

    public Integer createPayment(@Valid PaymentRequest paymentRequest) {
        var payment = repository.save(mapper.toPayment(paymentRequest));
        producer.sendNotification(
                new PaymentNotificationRequest(
                        paymentRequest.orderReference(),
                        paymentRequest.amount(),
                        paymentRequest.paymentMethod(),
                        paymentRequest.customer().firstName(),
                        paymentRequest.customer().lastName(),
                        paymentRequest.customer().email()
                )
        );
        return payment.getId();
    }
}
