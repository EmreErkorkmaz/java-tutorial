package com.javatutorial.order_service.client;

import com.javatutorial.order_service.exception.ProductAccessDeniedException;
import com.javatutorial.order_service.exception.ProductNotFoundException;
import com.javatutorial.order_service.exception.ProductServiceUnavailableException;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class ProductClient {
    private final RestClient restClient;

    public ProductClient(RestClient productRestClient) {
        this.restClient = productRestClient;
    }

    // Retry only the "dependency is unhealthy" case. A 404 is a correct answer, not a
    // failure - retrying it would just waste time and hammer a working service.
    @Retryable(
            includes = ProductServiceUnavailableException.class,
            maxRetries = 2, // 3 attempts in total
            delay = 200, // wait 200ms before the first retry
            multiplier = 2.0, // then 400ms - exponential backoff
            jitter = 50) // randomise a little so retries do not arrive in lockstep
    @ConcurrencyLimit(20) // bulkhead: at most 20 threads may sit in this call at once
    public ProductView findById(Long productId) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productId) // placeholder, not string concat: URL-encoded for us
                    .retrieve()
                    // Translate the remote 404 into our own domain error. Without this the
                    // caller would get a raw HTTP exception and answer 500 for a client mistake.
                    .onStatus(status -> status.value() == 404,
                            (request, response) ->
                            {
                                throw new ProductNotFoundException(productId);
                            })
                    // Downstream refused our identity. In this system product reads have no
                    // per-user rules, so this means our propagation is broken or the token
                    // expired mid-flight - an integration fault, not a user mistake.
                    .onStatus(status -> status.value() == 401 || status.value() == 403,
                            (request, response) -> {
                                throw new ProductAccessDeniedException(response.getStatusCode().value());
                            })
                    .body(ProductView.class);
        } catch (ResourceAccessException e) {
            // timeout, connection refused, DNS failure - the dependency is down, not the request wrong
            throw new ProductServiceUnavailableException(e);
        }
    }

    // Only the fields we need. product-service may add more; ignoring them keeps us decoupled.
    public record ProductView(Long id, String name, BigDecimal price) {
    }
}