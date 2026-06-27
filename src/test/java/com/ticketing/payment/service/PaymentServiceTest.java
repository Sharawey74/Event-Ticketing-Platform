package com.ticketing.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.Ticket;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.event.model.Event;
import com.ticketing.payment.dto.CheckoutSessionResponse;
import com.ticketing.payment.model.Payment;
import com.ticketing.payment.repository.PaymentRepository;
import com.ticketing.user.model.User;

/**
 * Unit tests for PaymentService — TDD Day 9.
 * All Stripe SDK calls are mocked via MockedStatic — no real Stripe API calls.
 *
 * NOTE: @Value fields are not injected in pure Mockito unit tests.
 * ReflectionTestUtils.setField() is used to set successUrl/cancelUrl directly.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private User testUser;
    private Event testEvent;
    private TicketTier testTier;
    private Booking reservedBooking;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that aren't populated by Mockito
        ReflectionTestUtils.setField(paymentService, "successUrl",
                "http://localhost:3000/bookings/{bookingId}/confirmation?session_id={CHECKOUT_SESSION_ID}");
        ReflectionTestUtils.setField(paymentService, "cancelUrl", "http://localhost:3000/cart");

        testUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .firstName("Test")
                .lastName("User")
                .build();

        testEvent = Event.builder()
                .id(10L)
                .title("Summer Concert")
                .build();

        testTier = TicketTier.builder()
                .id(100L)
                .tierName("VIP")
                .basePrice(new BigDecimal("99.00"))
                .event(testEvent)
                .availableCount(50)
                .build();

        reservedBooking = Booking.builder()
                .id(42L)
                .user(testUser)
                .event(testEvent)
                .state(BookingState.RESERVED)
                .totalAmount(new BigDecimal("99.00"))
                .expiresAt(Instant.now().plusSeconds(300))
                .createdAt(Instant.now())
                .tickets(new java.util.ArrayList<>())
                .build();

        // Add one ticket with back-reference
        Ticket ticket = Ticket.builder()
                .id(1L)
                .booking(reservedBooking)
                .tier(testTier)
                .checkInStatus(false)
                .build();
        reservedBooking.getTickets().add(ticket);
    }

    @Test
    @DisplayName("createCheckoutSession: for valid booking in RESERVED state should return Stripe URL")
    void createCheckoutSession_forValidBooking_inReservedState_shouldReturnStripeUrl() throws Exception {
        // Arrange
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(reservedBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findByBookingId(42L)).thenReturn(Optional.empty()); // first checkout — no existing payment
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Mock the static Session.create() — Stripe SDK uses static factory methods
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session_abc123");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_abc123");

        try (MockedStatic<Session> sessionMock = Mockito.mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // Act
            CheckoutSessionResponse response = paymentService.createCheckoutSession(42L, 1L);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_abc123");
            assertThat(response.getSessionId()).isEqualTo("cs_test_session_abc123");
            assertThat(response.getBookingId()).isEqualTo(42L);
            verify(paymentRepository).save(any(Payment.class));
            verify(bookingRepository).save(any(Booking.class));
        }
    }

    @Test
    @DisplayName("createCheckoutSession: when booking is not in RESERVED state should throw IllegalStateException")
    void createCheckoutSession_whenBookingNotInReservedState_shouldThrowConflictException() {
        // Arrange — booking is CONFIRMED, not RESERVED
        Booking confirmedBooking = Booking.builder()
                .id(42L)
                .user(testUser)
                .event(testEvent)
                .state(BookingState.CONFIRMED)
                .totalAmount(new BigDecimal("99.00"))
                .createdAt(Instant.now())
                .tickets(List.of())
                .build();

        when(bookingRepository.findById(42L)).thenReturn(Optional.of(confirmedBooking));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.createCheckoutSession(42L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESERVED");

        // No Stripe session created, no payment persisted
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCheckoutSession: when booking is not owned by requesting user should throw AccessDeniedException")
    void createCheckoutSession_whenBookingNotOwnedByUser_shouldThrowForbiddenException() {
        // Arrange — booking belongs to user 1L, but request comes from user 99L
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(reservedBooking));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.createCheckoutSession(42L, 99L))
                .isInstanceOf(AccessDeniedException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCheckoutSession: resuming a PAYMENT_PENDING booking updates existing payment row (no duplicate insert)")
    void createCheckoutSession_whenPaymentPendingAndNotExpired_shouldUpdateExistingPaymentAndReturnSession() throws Exception {
        // Arrange — booking already in PAYMENT_PENDING (abandoned checkout), hold still valid
        reservedBooking.setState(BookingState.PAYMENT_PENDING);
        reservedBooking.setExpiresAt(Instant.now().plusSeconds(600));

        // Existing payment row that was created on the first checkout attempt
        Payment existingPayment = Payment.builder()
                .id(99L)
                .booking(reservedBooking)
                .stripeSessionId("cs_test_old_session")
                .amount(new BigDecimal("99.00"))
                .currency("USD")
                .status(com.ticketing.payment.model.PaymentStatus.PENDING)
                .build();

        when(bookingRepository.findById(42L)).thenReturn(Optional.of(reservedBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        // Return the existing row — service must UPDATE it, not INSERT a new one
        when(paymentRepository.findByBookingId(42L)).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_resume_xyz");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_resume_xyz");

        try (MockedStatic<Session> sessionMock = Mockito.mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            // Act
            CheckoutSessionResponse response = paymentService.createCheckoutSession(42L, 1L);

            // Assert — existing payment row has new session ID; no duplicate row inserted
            assertThat(response).isNotNull();
            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_resume_xyz");
            assertThat(existingPayment.getStripeSessionId()).isEqualTo("cs_test_resume_xyz");
            assertThat(reservedBooking.getState()).isEqualTo(BookingState.PAYMENT_PENDING);
            verify(paymentRepository).save(existingPayment); // same object, not a new one
        }
    }

    @Test
    @DisplayName("createCheckoutSession: when the reservation hold has expired should throw IllegalStateException")
    void createCheckoutSession_whenExpired_shouldThrowIllegalState() {
        // Arrange — RESERVED but the 5-minute hold has already lapsed
        reservedBooking.setExpiresAt(Instant.now().minusSeconds(10));
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(reservedBooking));

        // Act + Assert
        assertThatThrownBy(() -> paymentService.createCheckoutSession(42L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        verify(paymentRepository, never()).save(any());
    }
}
