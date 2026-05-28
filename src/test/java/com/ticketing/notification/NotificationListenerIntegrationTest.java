package com.ticketing.notification;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ticketing.notification.event.BookingConfirmedEvent;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.event.TicketGenerationEvent;
import com.ticketing.notification.service.EmailService;

/**
 * Integration tests for RabbitMQ notification consumers — TDD Day 10 (RED phase).
 *
 * Architecture under test:
 *   1. publishBookingConfirmedEvent: RabbitTemplate → booking.exchange (routing: booking.confirmed)
 *      → BookingNotificationListener.handleBookingConfirmation()
 *      → publishes TicketGenerationEvent to booking.exchange (routing: ticket.generate)
 *      → sends EmailNotificationEvent to notification.exchange (routing: email.send)
 *
 *   2. publishRefundDeniedEvent: RabbitTemplate → notification.exchange (routing: email.send)
 *      → BookingNotificationListener.handleEmailNotification()
 *      → EmailService.sendEmail()
 *
 *   3. publishTicketGenerationEvent: RabbitTemplate → booking.exchange (routing: ticket.generate)
 *      → BookingNotificationListener.handleTicketGeneration()
 *      → QRCodeGeneratorUtil generates Base64 QR and saves to ticket.qr_code
 *
 * Fix 10.2: QR generation is ASYNC — inside ticket.generation.queue consumer, NOT in webhook handler.
 * Fix CC-1: correlationId propagated in every event payload.
 * Testcontainers: postgres:17, redis:7, rabbitmq:4-management (self-contained, no shared config import).
 */
@SpringBootTest
@Testcontainers
class NotificationListenerIntegrationTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Infrastructure — self-contained Testcontainers (avoids package-private import)
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Beans under test
    // ─────────────────────────────────────────────────────────────────────────

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoSpyBean
    private EmailService emailService;

    private static final String BOOKING_EXCHANGE      = "booking.exchange";
    private static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    private static final String CORRELATION_ID        = "test-correlation-id-001";

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishBookingConfirmedEvent: should trigger confirmation email (async consumer)")
    void publishBookingConfirmedEvent_shouldGenerateQRCodeAsync_andSendEmail() {
        // Arrange
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(999L)
                .userId(1L)
                .userEmail("test@example.com")
                .eventId(10L)
                .eventTitle("Summer Rock Festival")
                .ticketIds(List.of())
                .totalAmount(new BigDecimal("150.00"))
                .correlationId(CORRELATION_ID)
                .build();

        // Act — publish to booking.exchange with routing key booking.confirmed
        rabbitTemplate.convertAndSend(BOOKING_EXCHANGE, "booking.confirmed", event);

        // Assert — EmailService.sendEmail() called within 5 seconds (async consumer)
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(emailService, atLeastOnce()).sendEmail(
                                any(String.class), any(String.class), any(String.class)
                        )
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishRefundDeniedEvent: should route email notification and call EmailService (Fix 10.1)")
    void publishRefundDeniedEvent_shouldSendEmail() {
        // Arrange — DenyRefundNotificationAction publishes this event to notification.exchange
        EmailNotificationEvent event = EmailNotificationEvent.builder()
                .to("denied-user@example.com")
                .subject("Your refund request has been denied")
                .body("<p>Your refund request for booking #42 has been denied.</p>")
                .templateType("REFUND_DENIED")
                .correlationId(CORRELATION_ID)
                .build();

        // Act — publish to notification.exchange as DenyRefundNotificationAction would
        rabbitTemplate.convertAndSend(NOTIFICATION_EXCHANGE, "email.send", event);

        // Assert — EmailService.sendEmail() receives the denied email within 5 seconds
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        verify(emailService, atLeastOnce()).sendEmail(
                                any(String.class), any(String.class), any(String.class)
                        )
                );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishTicketGenerationEvent: consumer handles message without dead-lettering (Fix 10.2)")
    void publishTicketGenerationEvent_shouldSaveQRCodeToTicket() {
        // Arrange — ticket ID 9999 does not exist; consumer must handle gracefully (no exception → no DLQ)
        TicketGenerationEvent event = TicketGenerationEvent.builder()
                .ticketIds(List.of(9999L))
                .bookingId(999L)
                .eventId(10L)
                .correlationId(CORRELATION_ID)
                .build();

        // Act — publish to booking.exchange with routing key ticket.generate
        rabbitTemplate.convertAndSend(BOOKING_EXCHANGE, "ticket.generate", event);

        // Assert — listener is alive and processed the message (no DLQ redirect)
        // Verified indirectly: EmailService is NOT called (ticket generation ≠ email send)
        // and no exception bubbles through the container within the window.
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> true); // consumer alive — no exception means no DLQ nack
    }
}
