package com.shah.ecommerce.config;

import com.shah.ecommerce.security.ServiceTokenProvider;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor serviceTokenRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return template -> template.header(AUTHORIZATION, "Bearer " + serviceTokenProvider.getToken());
    }
}
