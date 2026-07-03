package com.ticketing.common.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ticketing.common.util.BusinessConstants;
import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

import org.springframework.test.web.servlet.MockMvc;

/**
 * M-002 — verifies {@code RateLimitFilter} actually enforces limits when
 * {@code app.rate-limit.enabled=true} (production behaviour), using the real security chain.
 */
@SpringBootTest(properties = "app.rate-limit.enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class RateLimitEnforcedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17"));

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4-management"));

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7")).withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private String validToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("ratelimit-user-" + java.util.UUID.randomUUID() + "@test.com")
                .passwordHash("irrelevant-not-checked-for-token-auth")
                .role(Role.USER)
                .build());

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password("irrelevant")
                .roles(Role.USER.name())
                .build();

        validToken = jwtService.generateToken(userDetails);
    }

    @Test
    @DisplayName("After the auth rate limit is exceeded, the next login request returns 429")
    void afterLimitExceeded_shouldReturn429() throws Exception {
        for (int i = 0; i < BusinessConstants.RATE_LIMIT_AUTH_REQUESTS_PER_MINUTE; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"ratelimit-probe@test.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().is(not(HttpStatus.TOO_MANY_REQUESTS.value())));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ratelimit-probe@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/bookings without an Idempotency-Key header returns 400")
    void bookingReserveWithoutIdempotencyKey_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1,\"tierId\":1,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Idempotency-Key")));
    }

    @Test
    @DisplayName("POST /api/v1/payments/webhook is excluded from rate limiting")
    void stripeWebhook_isExcludedFromRateLimiting() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/payments/webhook")
                            .header("Stripe-Signature", "t=123,v1=abc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"payment_intent.succeeded\"}"))
                    .andExpect(status().is(not(HttpStatus.TOO_MANY_REQUESTS.value())));
        }
    }
}
