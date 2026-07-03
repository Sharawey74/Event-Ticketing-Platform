package com.ticketing.common.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.common.dto.ApiResponse;
import com.ticketing.common.util.BusinessConstants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fix M-002 — per-client rate limiting on {@code /api/v1/auth/**} (by IP) and
 * {@code POST /api/v1/bookings} (by authenticated user), backed by an atomic Redis Lua
 * INCR+EXPIRE script (the same atomicity guarantee as {@code DistributedLockService}'s
 * SET-NX pattern — never a two-step INCR-then-EXPIRE).
 *
 * Registered as a {@code @Bean} inside {@link SecurityConfig} (never a scanned
 * {@code @Component}), so it is never instantiated inside a {@code @WebMvcTest} slice.
 *
 * Gated by {@code app.rate-limit.enabled} (default {@code false}) — there is no dedicated
 * {@code test} Spring profile in this project (all tests run under {@code local}), so a
 * property flag is the only way to keep every existing test suite unaffected while still
 * enforcing the limit in production.
 */
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String BOOKINGS_PATH = "/api/v1/bookings";
    private static final String STRIPE_WEBHOOK_PATH = "/api/v1/payments/webhook";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
        "local current = redis.call('INCR', KEYS[1]) " +
        "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
        "return current",
        Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return STRIPE_WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        if (path.startsWith(AUTH_PATH_PREFIX)) {
            String key = BusinessConstants.RATE_LIMIT_REDIS_PREFIX_AUTH + getClientIp(request);
            if (isRateLimitExceeded(key, BusinessConstants.RATE_LIMIT_AUTH_REQUESTS_PER_MINUTE)) {
                sendTooManyRequests(response, path, key);
                return;
            }
        } else if ("POST".equals(request.getMethod()) && BOOKINGS_PATH.equals(path)) {
            String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                sendMissingIdempotencyKey(response);
                return;
            }

            // Rate-limit key is the authenticated email (JWT subject) — JwtFilter runs before
            // this filter in the chain, so the SecurityContext is already populated here.
            String userKey = getAuthenticatedUsername();
            if (userKey != null) {
                String key = BusinessConstants.RATE_LIMIT_REDIS_PREFIX_BOOKING + userKey;
                if (isRateLimitExceeded(key, BusinessConstants.RATE_LIMIT_BOOKING_REQUESTS_PER_MINUTE)) {
                    sendTooManyRequests(response, path, key);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitExceeded(String key, int limitPerWindow) {
        Long count = redisTemplate.execute(
            RATE_LIMIT_SCRIPT,
            Collections.singletonList(key),
            String.valueOf(BusinessConstants.RATE_LIMIT_WINDOW_SECONDS));
        return count != null && count > limitPerWindow;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
            ? forwarded.split(",")[0].trim()
            : request.getRemoteAddr();
    }

    private String getAuthenticatedUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
            ? auth.getName()
            : null;
    }

    private void sendMissingIdempotencyKey(HttpServletResponse response) throws IOException {
        writeJsonError(response, HttpStatus.BAD_REQUEST,
            "Idempotency-Key header is required for POST /api/v1/bookings");
    }

    private void sendTooManyRequests(HttpServletResponse response, String path, String key) throws IOException {
        log.warn("Rate limit exceeded — path={} key={}", path, key);
        writeJsonError(response, HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests. Please wait before trying again.");
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(message)));
    }
}
