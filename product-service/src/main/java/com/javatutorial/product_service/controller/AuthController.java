package com.javatutorial.product_service.controller;

import com.javatutorial.product_service.dto.LoginRequest;
import com.javatutorial.product_service.dto.RegisterRequest;
import com.javatutorial.product_service.dto.TokenResponse;
import com.javatutorial.product_service.dto.UserResponse;
import com.javatutorial.product_service.exception.TooManyAttemptsException;
import com.javatutorial.product_service.service.AppUserService;
import com.javatutorial.product_service.service.LoginAttemptService;
import com.javatutorial.product_service.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginAttemptService loginAttemptService;
    private final AppUserService userService;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final long validityMinutes;

    public AuthController(
            LoginAttemptService loginAttemptService,
            AppUserService userService,
            AuthenticationManager authenticationManager,
            TokenService tokenService,
            @Value("${security.jwt.validity-minutes}") long validityMinutes
    ) {
        this.loginAttemptService = loginAttemptService;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.validityMinutes = validityMinutes;
    }

    @PostMapping
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        if (loginAttemptService.isBlocked(request.username())) {
            throw new TooManyAttemptsException(request.username());
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
            loginAttemptService.reset(authentication.getName()); // success clears the counter
            return new TokenResponse(tokenService.issue(authentication), "Bearer", validityMinutes * 60);
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(request.username());
            throw e; // Spring Security still turns this into a 401
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(userService.register(request.username(), request.password()));
    }
}
