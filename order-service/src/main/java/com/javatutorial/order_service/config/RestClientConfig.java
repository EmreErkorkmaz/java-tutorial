package com.javatutorial.order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor(bearerTokenPropagation())
                .build();
    }

    // Forwards the caller's already-verified token downstream, so product-service sees the
    // real user instead of an anonymous call. Taken from the SecurityContext rather than the
    // raw HTTP header: that way we forward the token Spring actually validated.
    private ClientHttpRequestInterceptor bearerTokenPropagation() {
        return (request, body, execution) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                request.getHeaders().setBearerAuth(jwtAuthentication.getToken().getTokenValue());
            }
            // No token in context (a scheduled job, for example): the call goes out anonymous
            // and downstream decides. We do not invent an identity here.
            return execution.execute(request, body);
        };
    }
}