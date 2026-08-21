package com.ticketing.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.stripe.model.checkout.Session;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.user.model.User;

import jakarta.persistence.EntityNotFoundException;

/**
 * Reconciliation is the fallback for the one signal that confirms a payment.
 *
 * Confirmation depends solely on Stripe's asynchronous `checkout.session.completed`
 * webhook. When that never arrives — no endpoint registered, an unreachable host,
 * or a delivery that is simply lost — the booking sits at PAYMENT_PENDING until
 * ReservationExpirationJob sweeps it to PAYMENT_FAILED, after the card was charged.
 *
 * Stripe puts `session_id` in the redirect URL precisely so the application can
 * verify on return. These tests pin that behaviour.
 */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private WebhookService webhookService;

    private TestableReconciliationService service;
    private Booking booking;
    private Session session;

    /**
     * Session.retrieve is a Stripe static. Overriding one seam keeps the test free
     * of static mocking without inventing a gateway abstraction for a single call.
     */
    private static class TestableReconciliationService extends PaymentReconciliationService {
        private Session stubbed;
        private String requestedSessionId;

        TestableReconciliationService(BookingRepository bookingRepository, WebhookService webhookService) {
            super(bookingRepository, webhookService);
        }

        void stubSession(Session session) {
            this.stubbed = session;
        }

        String requestedSessionId() {
            return requestedSessionId;
        }

        @Override
        protected Session retrieveSession(String sessionId) {
            this.requestedSessionId = sessionId;
            return stubbed;
        }
    }

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(7L);

        booking = new Booking();
        booking.setId(563L);
        booking.setUser(owner);
        booking.setState(BookingState.PAYMENT_PENDING);
        booking.setStripeSessionId("cs_test_abc123");

        session = new Session();
        session.setId("cs_test_abc123");

        service = new TestableReconciliationService(bookingRepository, webhookService);
        service.stubSession(session);
    }

    @Test
    @DisplayName("reconcile_whenStripeReportsPaid_shouldConfirmViaSharedWebhookPath")
    void reconcile_whenStripeReportsPaid_shouldConfirmViaSharedWebhookPath() {
        session.setPaymentStatus("paid");
        when(bookingRepository.findById(563L)).thenReturn(Optional.of(booking));

        BookingState result = service.reconcile(563L, 7L);

        assertThat(service.requestedSessionId()).isEqualTo("cs_test_abc123");
        // Delegates rather than reimplementing: the reconciliation path and the
        // webhook path must confirm identically, or they will drift.
        verify(webhookService).confirmPaidBooking(any(Session.class), any());
        assertThat(result).isEqualTo(BookingState.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("reconcile_whenStripeReportsUnpaid_shouldNotConfirm")
    void reconcile_whenStripeReportsUnpaid_shouldNotConfirm() {
        session.setPaymentStatus("unpaid");
        when(bookingRepository.findById(563L)).thenReturn(Optional.of(booking));

        service.reconcile(563L, 7L);

        verify(webhookService, never()).confirmPaidBooking(any(), any());
    }

    @Test
    @DisplayName("reconcile_whenBookingAlreadyConfirmed_shouldBeNoOpAndNotCallStripe")
    void reconcile_whenBookingAlreadyConfirmed_shouldBeNoOpAndNotCallStripe() {
        booking.setState(BookingState.CONFIRMED);
        when(bookingRepository.findById(563L)).thenReturn(Optional.of(booking));

        BookingState result = service.reconcile(563L, 7L);

        assertThat(result).isEqualTo(BookingState.CONFIRMED);
        assertThat(service.requestedSessionId()).isNull();
        verify(webhookService, never()).confirmPaidBooking(any(), any());
    }

    @Test
    @DisplayName("reconcile_whenBookingBelongsToAnotherUser_shouldDenyAccess")
    void reconcile_whenBookingBelongsToAnotherUser_shouldDenyAccess() {
        when(bookingRepository.findById(563L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.reconcile(563L, 999L))
                .isInstanceOf(AccessDeniedException.class);

        verify(webhookService, never()).confirmPaidBooking(any(), any());
    }

    @Test
    @DisplayName("reconcile_whenBookingMissing_shouldThrowEntityNotFound")
    void reconcile_whenBookingMissing_shouldThrowEntityNotFound() {
        when(bookingRepository.findById(563L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reconcile(563L, 7L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("reconcile_whenNoStripeSessionOnBooking_shouldNotCallStripe")
    void reconcile_whenNoStripeSessionOnBooking_shouldNotCallStripe() {
        booking.setStripeSessionId(null);
        when(bookingRepository.findById(563L)).thenReturn(Optional.of(booking));

        BookingState result = service.reconcile(563L, 7L);

        assertThat(result).isEqualTo(BookingState.PAYMENT_PENDING);
        assertThat(service.requestedSessionId()).isNull();
        verify(webhookService, never()).confirmPaidBooking(any(), any());
    }
}
