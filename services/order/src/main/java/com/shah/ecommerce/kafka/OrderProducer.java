package com.shah.ecommerce.kafka;

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

@RequiredArgsConstructor
@Service
@Slf4j
public class OrderProducer {

    private final KafkaTemplate<String, OrderConfirmation> kafkaTemplate;

    public void sendOrderConfirmation(OrderConfirmation orderConfirmation) {
        log.info("Sending order confirmation");
        Message<OrderConfirmation> message = MessageBuilder
                .withPayload(orderConfirmation)
                .setHeader(KafkaHeaders.TOPIC, "order-topic")
                .build();
        try {
            var future = kafkaTemplate.send(message);
            if (future != null) {
                future.get(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(
                    "Order confirmation delivery was interrupted because Messaging Service is unavailable."
            );
        } catch (KafkaException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new ServiceUnavailableException(
                    "Notification Messaging Service (Kafka) is currently unavailable. Please try again later."
            );
        }
    }
}
