package com.shah.ecommerce.config;

import com.shah.ecommerce.security.ServiceTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ResTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ServiceTokenProvider serviceTokenProvider){
        var restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().setBearerAuth(serviceTokenProvider.getToken());
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
