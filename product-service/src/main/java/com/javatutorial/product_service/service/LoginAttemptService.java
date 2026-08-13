package com.javatutorial.product_service.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private record Failures(int count, Instant windowStart) {}

    // In-memory and per-instance: this is the WRONG layer for production. Behind a load
    // balancer each instance would count separately, and a restart forgets everything.
    // Real systems rate-limit at the gateway/proxy (nginx, API gateway, Cloudflare) or
    // with a shared store like Redis. Kept in-app here to make the mechanism visible.
    private final Map<String, Failures> byUsername = new ConcurrentHashMap<>();

    public boolean isBlocked(String username) {
        Failures failures = byUsername.get(username);
        if (failures == null) {
            return false;
        }
        if (Instant.now().isAfter(failures.windowStart().plus(WINDOW))) {
            byUsername.remove(username);
            return false;
        }
        return failures.count() >= MAX_FAILURES;
    }

    public void recordFailure(String username) {
        byUsername.compute(username, (key, existing) -> {
            Instant now = Instant.now();
            if (existing == null || now.isAfter(existing.windowStart().plus(WINDOW))) {
                return new Failures(1, now);
            }
            return new Failures(existing.count() + 1, existing.windowStart());
        });
    }

    public void reset(String username) {
        byUsername.remove(username); // a successful login clears the counter
    }
}
