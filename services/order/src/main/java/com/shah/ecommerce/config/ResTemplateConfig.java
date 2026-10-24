package com.shah.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class ResTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ServiceTokenProvider tokenProvider) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().set(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + tokenProvider.createInternalServiceToken()
            );
            return execution.execute(request, body);
        }));
        return restTemplate;
    }
}
