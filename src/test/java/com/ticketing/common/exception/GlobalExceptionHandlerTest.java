package com.ticketing.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketing.common.config.TestSecurityConfig;
import com.ticketing.common.security.JwtService;

/**
 * Tests that GlobalExceptionHandler maps exceptions to the correct HTTP status codes.
 * Uses TestStubController to trigger each exception type.
 *
 * TDD gate for Fix 9: IllegalStateException → 409 Conflict (was falling to 500 catch-all).
 */
@WebMvcTest(controllers = TestStubController.class)
@Import(TestSecurityConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockUser
    void throwIllegalState_shouldReturn409WithMessage() throws Exception {
        mockMvc.perform(post("/test/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Checkout requires booking in RESERVED state, but was: CANCELLED"));
    }
}
