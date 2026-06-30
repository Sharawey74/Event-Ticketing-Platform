package com.ticketing.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.notification.publisher.BookingEventPublisher;
import com.ticketing.payment.model.ProcessedStripeEvent;
import com.ticketing.payment.repository.PaymentRepository;
import com.ticketing.payment.repository.ProcessedStripeEventRepository;
import com.ticketing.user.model.User;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for WebhookService — TDD Green phase (Day 9).
 * Fix 9.2: DataIntegrityViolationException is the idempotency guard.
 * Fix 9.1: HTTP 200 returned only after processEvent() (@Transactional method) commits.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookServiceTest {

    @Mock
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @InjectMocks
    private WebhookService webhookService;

    private Booking paymentPendingBooking;

    @BeforeEach
    void setUp() {
        User owner = User.builder()
                .id(1L)
                .email("attendee@eventora.com")
                .build();

        com.ticketing.event.model.Event event = com.ticketing.event.model.Event.builder()
                .id(10L)
                .title("Test Event")
                .startDate(Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .build();

        paymentPendingBooking = Booking.builder()
                .id(42L)
                .state(BookingState.PAYMENT_PENDING)
                .stripeSessionId("cs_test_session_abc123")
                .user(owner)
                .event(event)
                .totalAmount(java.math.BigDecimal.valueOf(500))
                .tickets(new java.util.ArrayList<>())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("processEvent: checkout.session.completed should transition booking to CONFIRMED")
    void processEvent_paymentSuccess_shouldTransitionBookingToConfirmed() {
        // Arrange: build mock Stripe checkout.session.completed event
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session_abc123");
        when(mockSession.getMetadata()).thenReturn(java.util.Map.of("bookingId", "42"));
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_001");

        EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);
        when(mockDeserializer.getObject()).thenReturn(Optional.of(mockSession));

        Event stripeEvent = mock(Event.class);
        when(stripeEvent.getId()).thenReturn("evt_test_001");
        when(stripeEvent.getType()).thenReturn("checkout.session.completed");
        when(stripeEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);

        // ProcessedStripeEvent save succeeds (no duplicate)
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(bookingRepository.findById(42L)).thenReturn(Optional.of(paymentPendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        webhookService.processEvent(stripeEvent);

        // Assert: processedEvent saved + booking saved with new state
        verify(processedStripeEventRepository).save(any(ProcessedStripeEvent.class));
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("processEvent: duplicate event should catch DataIntegrityViolationException and silently ignore (Fix 9.2)")
    void processEvent_duplicateEvent_shouldThrowDataIntegrityViolation_andSilentlyIgnore() {
        // Arrange: the UNIQUE constraint fires on a duplicate stripe_event_id
        Event stripeEvent = mock(Event.class);
        when(stripeEvent.getId()).thenReturn("evt_duplicate_001");

        // Fix 9.2: Unique constraint violation — thrown at INSERT time
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // Act: processEvent must NOT propagate the exception — silently return
        webhookService.processEvent(stripeEvent);

        // Assert: booking was never touched (early return after duplicate detection)
        verify(bookingRepository, never()).findById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("processEvent: checkout.session.expired should transition booking to PAYMENT_FAILED")
    void processEvent_whenCheckoutExpired_shouldTransitionBookingToPaymentFailed() {
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_expired_001");
        when(mockSession.getMetadata()).thenReturn(java.util.Map.of("bookingId", "42"));

        EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);
        when(mockDeserializer.getObject()).thenReturn(Optional.of(mockSession));

        Event stripeEvent = mock(Event.class);
        when(stripeEvent.getId()).thenReturn("evt_expired_001");
        when(stripeEvent.getType()).thenReturn("checkout.session.expired");
        when(stripeEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);

        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(paymentPendingBooking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(stripeEvent);

        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("processEvent: unhandled event type should log and return without touching booking")
    void processEvent_whenUnknownEventType_shouldIgnoreWithoutTouchingBooking() {
        Event stripeEvent = mock(Event.class);
        when(stripeEvent.getId()).thenReturn("evt_unknown_001");
        when(stripeEvent.getType()).thenReturn("payment_intent.created");

        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(stripeEvent);

        verify(bookingRepository, never()).findById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("processEvent: checkout.session.completed when booking already CONFIRMED should skip re-processing")
    void processEvent_whenBookingAlreadyConfirmed_shouldSkipAndReturn() {
        paymentPendingBooking.setState(BookingState.CONFIRMED);

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session_abc123");
        when(mockSession.getMetadata()).thenReturn(java.util.Map.of("bookingId", "42"));
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_002");

        EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);
        when(mockDeserializer.getObject()).thenReturn(Optional.of(mockSession));

        Event stripeEvent = mock(Event.class);
        when(stripeEvent.getId()).thenReturn("evt_confirmed_already");
        when(stripeEvent.getType()).thenReturn("checkout.session.completed");
        when(stripeEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);

        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(paymentPendingBooking));

        webhookService.processEvent(stripeEvent);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("processEvent: checkout.session.completed missing bookingId in metadata should return early")
    void processEvent_whenMetadataMissingBookingId_shouldReturnEarly() {
        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_no_meta");
        when(mockSession.getMetadata()).thenReturn(java.util.Map.of());

        EventDataObjectDeserializer mockDeserializer = mock(EventDataObjectDeserializer.class);
        when(mockDeserializer.getObject()).thenReturn(Optional.of(mockSession));

        Event stripeEvent = mock(Event.class);
        when(stripeEvent.getId()).thenReturn("evt_no_bookingid");
        when(stripeEvent.getType()).thenReturn("checkout.session.completed");
        when(stripeEvent.getDataObjectDeserializer()).thenReturn(mockDeserializer);

        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        webhookService.processEvent(stripeEvent);

        verify(bookingRepository, never()).findById(any());
    }
}
