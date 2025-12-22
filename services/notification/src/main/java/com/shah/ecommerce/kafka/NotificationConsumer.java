package com.shah.ecommerce.kafka;

import com.shah.ecommerce.email.EmailService;
import com.shah.ecommerce.kafka.order.OrderConfirmation;
import com.shah.ecommerce.kafka.payment.PaymentConfirmation;
import com.shah.ecommerce.notification.Notification;
import com.shah.ecommerce.notification.NotificationRepository;
import com.shah.ecommerce.notification.NotificationType;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.shah.ecommerce.notification.NotificationType.ORDER_CONFIRMATION;
import static com.shah.ecommerce.notification.NotificationType.PAYMENT_CONFIRMATION;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    private final NotificationRepository repository;
    private final EmailService emailService;

    @KafkaListener(topics = "payment-topic", groupId = "paymentGroup")
    public void consumePaymentSuccessNotification(PaymentConfirmation paymentConfirmation) throws MessagingException {
        log.info(String.format("Consuming the message from payment-topic Topic:: %s", paymentConfirmation));
        repository.save(
                Notification.builder()
                        .paymentConfirmation(paymentConfirmation)
                        .notificationType(PAYMENT_CONFIRMATION)
                        .notificationDate(LocalDateTime.now())
                        .build()
        );

//        send email
        var customerName = paymentConfirmation.customerFirstname() + " " + paymentConfirmation.customerLastname();
        emailService.sendPaymentSuccessEmail(paymentConfirmation.customerEmail(), customerName, paymentConfirmation.amount(), paymentConfirmation.orderReference());

    }

    @KafkaListener(topics = "order-topic", groupId = "orderGroup")
    public void consumeOrderConfirmationNotification(OrderConfirmation orderConfirmation) throws MessagingException {
        log.info(String.format("Consuming the message from order-topic Topic:: %s", orderConfirmation));
        repository.save(
                Notification.builder()
                        .orderConfirmation(orderConfirmation)
                        .notificationType(ORDER_CONFIRMATION)
                        .notificationDate(LocalDateTime.now())
                        .build()
        );

//       send email
        var customerName = orderConfirmation.customer().firstName() + " " + orderConfirmation.customer().lastName();
        emailService.sendOrderConfirmationEmail(orderConfirmation.customer().email(), customerName, orderConfirmation.totalAmount(), orderConfirmation.orderReference(),orderConfirmation.products());

    }


}
