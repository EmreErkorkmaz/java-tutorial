package com.javatutorial.product_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false) // only picked up by tests that explicitly import it
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection // Boot reads host/port/user/password off the container and rewires the DataSource:
    // no spring.datasource.* overrides needed in the test properties
    PostgreSQLContainer postgreSQLContainer() {
        // same image as compose.yaml - tests should run against what production runs
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
