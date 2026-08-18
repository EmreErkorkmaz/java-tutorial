package com.javatutorial.order_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NotFound", ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(ProductNotFoundException ex) {
        // 400, not 404: /api/orders exists, it is the product id INSIDE the request that is wrong
        return build(HttpStatus.BAD_REQUEST, "BadRequest", ex.getMessage());
    }

    @ExceptionHandler(OrderAlreadyReturnedException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyReturned(OrderAlreadyReturnedException ex) {
        // 409: the request is well formed, it conflicts with the order's current state
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler({ProductServiceUnavailableException.class, InvocationRejectedException.class})
    public ResponseEntity<Map<String, Object>> handleDependencyDown(RuntimeException ex) {
        // 503, not 500: nothing is wrong with our code or the request - a dependency is down.
        // The distinction matters operationally: 5xx from us means "fix the bug",
        // 503 means "the thing we call is unhealthy, retrying later may work".
        return build(HttpStatus.SERVICE_UNAVAILABLE, "ServiceUnavailable",
                "Product service is unavailable, please try again later");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>(Map.of(
                "timestamp", Instant.now(),
                "status", 400,
                "error", "BadRequest"));
        body.put("fields", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now(),
                "status", status.value(),
                "error", error,
                "message", message));
    }

    @ExceptionHandler(ProductAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamRejected(ProductAccessDeniedException ex) {
        // 502 Bad Gateway: the user was authorised with US, the failure is in the hop between
        // services. Returning 401 here would tell them to log in again, which fixes nothing.
        return build(HttpStatus.BAD_GATEWAY, "BadGateway",
                "Could not reach product service with a valid identity");
    }
}