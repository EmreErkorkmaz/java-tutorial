package com.javatutorial.order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient productRestClient(@Value("${product-service.base-url}") String baseUrl) {
        // Timeouts are the single most important setting here. Without them a hung
        // dependency holds this service's threads until they run out - one slow service
        // takes down the ones calling it.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)) // time to open the TCP connection
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3)); // time to wait for the response

        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}