package com.ticketing.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.common.security.JwtService;
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

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtService jwtService;

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

    /**
     * Fix M-004: new endpoint — JWT denylist logout. Missing/malformed Authorization header is
     * treated as a graceful no-op (200), not an error, since "log me out" should never fail
     * from the caller's point of view even if they're already unauthenticated.
     */
    @Operation(summary = "Log out", description = "Denylists the current JWT (by jti) for the remainder of its lifetime.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logout succeeded (or no-op if no token was supplied)")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            jwtService.revokeToken(token);
        } catch (Exception ex) {
            logger.warn("Logout called with an invalid JWT — ignoring", ex);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
