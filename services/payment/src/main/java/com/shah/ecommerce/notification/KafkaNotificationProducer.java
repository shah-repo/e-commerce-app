package com.shah.ecommerce.notification;

import com.shah.ecommerce.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationProducer {
    private final KafkaTemplate<String, PaymentNotificationRequest> template;

    public void sendNotification(PaymentNotificationRequest request) {
        log.info("Sending notification with body <{}>", request);
        Message<PaymentNotificationRequest> message = MessageBuilder
                .withPayload(request)
                .setHeader(KafkaHeaders.TOPIC, "payment-topic")
                .build();
        try {
            var future = template.send(message);
            if (future != null) {
                future.get(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(
                    "Payment notification delivery was interrupted because Messaging Service is unavailable."
            );
        } catch (KafkaException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new ServiceUnavailableException(
                    "Notification Messaging Service (Kafka) is currently unavailable. Please try again later."
            );
        }
    }
}
