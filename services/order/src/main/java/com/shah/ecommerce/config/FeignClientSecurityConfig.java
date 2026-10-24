package com.shah.ecommerce.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class FeignClientSecurityConfig {

    @Bean
    public RequestInterceptor internalServiceTokenInterceptor(ServiceTokenProvider tokenProvider) {
        return template -> template.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + tokenProvider.createInternalServiceToken()
        );
    }
}
