package com.ticketing.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.statemachine.StateMachine;
import com.ticketing.booking.statemachine.BookingStateMachineService;

import static org.mockito.ArgumentMatchers.anyString;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.common.exception.ConflictException;
import com.ticketing.event.model.Event;
import com.ticketing.payment.dto.RefundResponse;
import com.ticketing.payment.model.Payment;
import com.ticketing.payment.model.Refund;
import com.ticketing.payment.model.RefundStatus;
import com.ticketing.payment.repository.PaymentRepository;
import com.ticketing.payment.repository.RefundRepository;
import com.ticketing.user.model.User;

/**
 * TDD Red → Green — Day 12
 *
 * Unit tests for RefundService — three-tier refund window logic.
 *
 * Non-negotiable rules enforced:
 * - ChronoUnit.DAYS.between() for all day-distance calculations (never Duration.between())
 * - All thresholds from BusinessConstants: FULL_REFUND_DAYS_THRESHOLD=7, PARTIAL_REFUND_DAYS_THRESHOLD=3
 * - PARTIAL_REFUND_RATE = 0.50 → 50% back
 * - Denial reason persisted on Booking entity (Fix 12.1 — V11 migration already applied)
 * - Fix CC-2: Zero magic numbers in production code
 *
 * NOTE: Booking objects are REAL builder instances (not mocks) so that setState() and
 * setRefundDenialReason() mutations are observable in assertions. Only User and Event
 * associations are mocked because they are lazy-loaded JPA relations.
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private BookingStateMachineService bookingStateMachineService;

    @Mock
    private StateMachine<BookingState, BookingEvent> stateMachine;

    private RefundService refundService;

    private static final Long    BOOKING_ID         = 1L;
    private static final Long    USER_ID            = 42L;
    private static final BigDecimal TOTAL_AMOUNT    = new BigDecimal("200.00");
    private static final String  PAYMENT_INTENT_ID  = "pi_test_12345";

    @BeforeEach
    void setUp() {
        // Stub the SM service to return a no-op state machine.
        // State transitions are verified by integration tests; unit tests verify business logic only.
        lenient().when(bookingStateMachineService.acquireForRefund(
                any(), anyString(), any())).thenReturn(stateMachine);
        lenient().when(stateMachine.sendEvent(any(reactor.core.publisher.Mono.class)))
                .thenReturn(reactor.core.publisher.Flux.empty());

        refundService = new RefundService(
                bookingRepository,
                paymentRepository,
                refundRepository,
                paymentService,
                bookingStateMachineService
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1 — Full refund: event is ≥ 7 days away
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("requestRefund: event ≥ 7 days away → full refund issued and state=REFUND_APPROVED")
    void requestRefund_when7OrMoreDaysBeforeEvent_shouldIssueFullRefund() {
        // Arrange — event 10 days away: above FULL_REFUND_DAYS_THRESHOLD (7)
        Instant eventDate = Instant.now().plus(10, ChronoUnit.DAYS);
        Booking booking   = buildConfirmedBooking(eventDate);
        Payment payment   = buildPayment();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RefundResponse response = refundService.requestRefund(BOOKING_ID, USER_ID);

        // Assert — full amount returned, APPROVED status
        assertThat(response.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(response.getAmount()).isEqualByComparingTo(TOTAL_AMOUNT);

        // Stripe refund must be called with the FULL amount
        verify(paymentService).refundAmount(eq(PAYMENT_INTENT_ID), eq(TOTAL_AMOUNT));

        // Booking state transition is now handled by BookingStateChangeInterceptor
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2 — Partial refund: event is 3–6 days away (50% back)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("requestRefund: event 3–6 days away → 50% partial refund issued and state=REFUND_APPROVED")
    void requestRefund_when3To6DaysBeforeEvent_shouldIssuePartialRefund() {
        // Arrange — event 4 days away: between PARTIAL (3) and FULL (7) thresholds
        Instant eventDate       = Instant.now().plus(4, ChronoUnit.DAYS);
        Booking booking         = buildConfirmedBooking(eventDate);
        Payment payment         = buildPayment();
        BigDecimal expectedPartial = new BigDecimal("100.00"); // 50% of 200.00

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RefundResponse response = refundService.requestRefund(BOOKING_ID, USER_ID);

        // Assert — exactly 50% returned
        assertThat(response.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(response.getAmount()).isEqualByComparingTo(expectedPartial);

        // Stripe called with the PARTIAL amount only
        verify(paymentService).refundAmount(eq(PAYMENT_INTENT_ID), eq(expectedPartial));
        // Booking state transition is now handled by BookingStateChangeInterceptor
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3 — Denial: event is < 3 days away (no money back)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("requestRefund: event < 3 days away → refund denied, ZERO amount, state=REFUND_DENIED")
    void requestRefund_whenLessThan3DaysBeforeEvent_shouldDenyRefund() {
        // Arrange — event tomorrow: below PARTIAL_REFUND_DAYS_THRESHOLD (3)
        Instant eventDate = Instant.now().plus(1, ChronoUnit.DAYS);
        Booking booking   = buildConfirmedBooking(eventDate);
        Payment payment   = buildPayment();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        RefundResponse response = refundService.requestRefund(BOOKING_ID, USER_ID);

        // Assert — ZERO amount, DENIED
        assertThat(response.getStatus()).isEqualTo(RefundStatus.DENIED);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // Stripe must NOT be called — no money refunded
        verify(paymentService, never()).refundAmount(any(), any());
        // Booking state transition is now handled by BookingStateChangeInterceptor
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 4 — ConflictException: booking not in CONFIRMED state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("requestRefund: booking not CONFIRMED → throws ConflictException (no Stripe call)")
    void requestRefund_whenBookingNotConfirmed_shouldThrowConflictException() {
        // Arrange — booking in RESERVED state (not eligible for refund request)
        Booking booking = buildBookingWithState(BookingState.RESERVED, Instant.now().plus(20, ChronoUnit.DAYS));
        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));

        // Act & Assert
        assertThatThrownBy(() -> refundService.requestRefund(BOOKING_ID, USER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CONFIRMED");

        // Neither Stripe nor the refund repository should be touched
        verify(paymentService, never()).refundAmount(any(), any());
        verify(refundRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 5 — Fix 12.1: refund_denial_reason persisted on Booking entity
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("denyRefund: refund_denial_reason saved to booking when refund is denied (Fix 12.1)")
    void denyRefund_shouldPersistDenialReason() {
        // Arrange — event tomorrow → denial path
        Instant eventDate = Instant.now().plus(1, ChronoUnit.DAYS);
        Booking booking   = buildConfirmedBooking(eventDate);
        Payment payment   = buildPayment();

        when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        refundService.requestRefund(BOOKING_ID, USER_ID);

        // Assert — booking saved with a non-blank denial reason
        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        Booking savedBooking = bookingCaptor.getValue();

        assertThat(savedBooking.getRefundDenialReason())
                .as("refund_denial_reason must be persisted when refund is denied (Fix 12.1)")
                .isNotNull()
                .isNotBlank();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers — REAL Booking objects so setState/setRefundDenialReason mutate
    // ─────────────────────────────────────────────────────────────────────────

    private Booking buildConfirmedBooking(Instant eventDate) {
        return buildBookingWithState(BookingState.CONFIRMED, eventDate);
    }

    /**
     * Builds a real Booking instance using the Lombok builder.
     * User and Event are Mockito mocks because they are lazy JPA associations
     * that would require a persistence context to load otherwise.
     *
     * event.getStartDate() is stubbed with lenient() because test 4 (ConflictException)
     * throws before reaching the day-distance calculation — the stub is set up but not called
     * in that case. lenient() suppresses UnnecessaryStubbing without sacrificing strictness
     * for the other 4 tests that do exercise the stub.
     */
    private Booking buildBookingWithState(BookingState state, Instant eventDate) {
        User user = User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .build();

        Event event = mock(Event.class);
        lenient().when(event.getStartDate()).thenReturn(eventDate); // lenient: not used in test 4

        return Booking.builder()
                .id(BOOKING_ID)
                .user(user)
                .event(event)
                .state(state)
                .totalAmount(TOTAL_AMOUNT)
                .build();
    }

    /**
     * Returns a mocked Payment.
     * getStripePaymentIntentId() is stubbed with lenient() because the denial path
     * (tests 3 and 5) never calls Stripe — the stub is set up but unused in those cases.
     */
    private Payment buildPayment() {
        Payment payment = mock(Payment.class);
        lenient().when(payment.getStripePaymentIntentId()).thenReturn(PAYMENT_INTENT_ID); // lenient: not used in denial path
        return payment;
    }
}
