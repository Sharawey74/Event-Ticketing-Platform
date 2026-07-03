package com.ticketing.common.security;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketing.user.controller.AuthController;
import com.ticketing.user.service.AuthService;

/**
 * M-002 regression guard: {@code RateLimitFilter} is a {@code @Bean} wired only inside the real
 * {@code SecurityConfig} filter chain — it must never become a scanned {@code @Component}, or it
 * would load (and start rate-limiting) inside every {@code @WebMvcTest} slice in the project.
 *
 * This slice never loads {@code SecurityConfig} at all, so 15 rapid logins must never 429.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class RateLimitDisabledByDefaultTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Rate limiting is never active in a @WebMvcTest slice — 15 rapid requests should never hit 429")
    void rateLimitNeverActiveInWebMvcSlice_shouldNeverReturn429() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"test@test.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().is(not(HttpStatus.TOO_MANY_REQUESTS.value())));
        }
    }
}
