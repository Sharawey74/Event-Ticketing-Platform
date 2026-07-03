package com.ticketing.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.userdetails.UserDetails;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for M-004 — JWT denylist via {@code jti} + POST /api/v1/auth/logout.
 *
 * Verifies that a valid token grants access before logout, and that the SAME token is
 * rejected with 401 immediately after logout denylists its jti.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class JwtDenylistTest {

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
                .email("denylist-user-" + java.util.UUID.randomUUID() + "@test.com")
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
    @DisplayName("logout denylists the token's jti; the same token then returns 401 on the next request")
    void logout_shouldDenylistToken_andReturn401OnSubsequentRequest() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/my")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/bookings/my")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout with no Authorization header is a graceful no-op that returns 200")
    void logoutWithNoToken_shouldReturn200Gracefully() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());
    }
}
