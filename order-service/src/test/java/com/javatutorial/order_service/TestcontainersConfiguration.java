package com.javatutorial.order_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection // Boot reads host/port/credentials off the container and rewires the DataSource
    PostgreSQLContainer postgreSQLContainer() {
        return new PostgreSQLContainer("postgres:18-alpine"); // not generic in Testcontainers 2.x
    }
}
