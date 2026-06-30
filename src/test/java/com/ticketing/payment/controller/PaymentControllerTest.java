package com.ticketing.payment.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketing.common.config.TestSecurityConfig;
import com.ticketing.common.security.JwtService;
import com.ticketing.payment.dto.CheckoutSessionResponse;
import com.ticketing.payment.service.PaymentService;
import com.ticketing.user.service.CustomUserDetails;

@WebMvcTest(controllers = PaymentController.class)
@Import(TestSecurityConfig.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PaymentService paymentService;
    @MockitoBean private JwtService jwtService;

    private CustomUserDetails userPrincipal;

    @BeforeEach
    void setUp() {
        userPrincipal = new CustomUserDetails(
                1L, "user@test.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    @DisplayName("POST /api/bookings/{id}/checkout: authenticated user should receive 200 with checkout URL")
    void createCheckoutSession_whenAuthenticated_shouldReturn200() throws Exception {
        CheckoutSessionResponse response = CheckoutSessionResponse.builder()
                .checkoutUrl("https://checkout.stripe.com/c/pay/cs_test_abc123")
                .build();

        when(paymentService.createCheckoutSession(42L, 1L)).thenReturn(response);

        mockMvc.perform(post("/api/bookings/42/checkout")
                        .with(user(userPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.stripe.com/c/pay/cs_test_abc123"));
    }

    @Test
    @DisplayName("POST /api/bookings/{id}/checkout: unauthenticated request should receive 401")
    void createCheckoutSession_whenUnauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/bookings/42/checkout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/bookings/{id}/checkout: ORGANIZER role should also receive 200")
    void createCheckoutSession_whenOrganizer_shouldReturn200() throws Exception {
        CustomUserDetails organizerPrincipal = new CustomUserDetails(
                2L, "organizer@test.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));

        CheckoutSessionResponse response = CheckoutSessionResponse.builder()
                .checkoutUrl("https://checkout.stripe.com/c/pay/cs_test_org123")
                .build();

        when(paymentService.createCheckoutSession(10L, 2L)).thenReturn(response);

        mockMvc.perform(post("/api/bookings/10/checkout")
                        .with(user(organizerPrincipal)))
                .andExpect(status().isOk());
    }
}
