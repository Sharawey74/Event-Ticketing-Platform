package com.ticketing.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.common.security.JwtService;
import com.ticketing.user.dto.AuthResponse;
import com.ticketing.user.dto.LoginRequest;
import com.ticketing.user.dto.RegisterRequest;
import com.ticketing.user.model.Role;
import com.ticketing.user.service.AuthService;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/auth/register should return success response")
    void register_shouldReturnSuccessResponse() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password@123");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setRole(Role.USER);

        AuthResponse authResponse = AuthResponse.builder()
            .token("jwt-token")
            .userId(7L)
            .email("user@example.com")
            .role(Role.USER)
            .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").value("jwt-token"))
            .andExpect(jsonPath("$.data.userId").value(7));
    }

    @Test
    @DisplayName("POST /api/auth/login should return success response")
    void login_shouldReturnSuccessResponse() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password@123");

        AuthResponse authResponse = AuthResponse.builder()
            .token("jwt-login-token")
            .userId(21L)
            .email("user@example.com")
            .role(Role.ORGANIZER)
            .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").value("jwt-login-token"))
            .andExpect(jsonPath("$.data.userId").value(21));
    }
}
