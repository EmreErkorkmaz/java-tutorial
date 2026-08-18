package com.javatutorial.product_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice // like Express' global error middleware - applied to every controller automatically
public class GlobalExceptionHandler {

    @ExceptionHandler({ ProductNotFoundException.class, CategoryNotFoundException.class })
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now(),
                "status", 404,
                "error", "NotFound",
                "message", ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(UsernameAlreadyExistsException ex) {
        // 409 Conflict, not 400: the request itself is well-formed, it conflicts with existing state
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now(),
                "status", 409,
                "error", "Conflict",
                "message", ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class) // Spring throws this when @Valid fails
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // which field failed and why -> {"name": "Name can not be blank", "price": "..."}
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now(),
                "status", 400,
                "error", "BadRequest",
                "fields", fieldErrors // so the client knows exactly what to fix
        );

        return ResponseEntity.badRequest().body(body);
    }

    // ---- EKLE ----
    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(TooManyAttemptsException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now(),
                "status", 429,
                "error", "TooManyRequests",
                "message", "Too many failed attempts, try again later" // no username echoed back
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        // A bad request, not a server fault: the caller asked for something out of bounds
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now(),
                "status", 400,
                "error", "BadRequest",
                "message", ex.getMessage()
        );
        return ResponseEntity.badRequest().body(body);
    }

}
