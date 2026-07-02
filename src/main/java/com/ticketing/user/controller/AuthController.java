package com.ticketing.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.user.dto.AuthResponse;
import com.ticketing.user.dto.LoginRequest;
import com.ticketing.user.dto.RegisterRequest;
import com.ticketing.user.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    @Operation(summary = "Register a new user", description = "Creates a new ATTENDEE or ORGANIZER account. ADMIN self-registration is blocked.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Registration succeeded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed or role not allowed")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        logger.info("Register endpoint completed for {}", request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Log in", description = "Authenticates a user and returns a JWT access token.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login succeeded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        logger.info("Login endpoint completed for {}", request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
