package com.shah.ecommerce.config;

import com.shah.ecommerce.security.ServiceTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class ResTemplateConfig {

    private final ServiceTokenProvider serviceTokenProvider;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().set(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + serviceTokenProvider.createInternalServiceToken()
            );
            return execution.execute(request, body);
        }));
        return restTemplate;
    }
}
