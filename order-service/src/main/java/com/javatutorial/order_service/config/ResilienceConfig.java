package com.javatutorial.order_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods // without this, @Retryable and @ConcurrencyLimit are silently ignored
public class ResilienceConfig {
}
