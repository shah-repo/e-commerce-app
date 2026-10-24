package com.shah.ecommerce;

import com.shah.ecommerce.email.EmailService;
import com.shah.ecommerce.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
class NotificationApplicationTests {

    @MockitoBean
    private NotificationRepository notificationRepository;

    @MockitoBean
    private EmailService emailService;

    @Test
    void contextLoads() {
    }

}
