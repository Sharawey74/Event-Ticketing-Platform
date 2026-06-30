package com.ticketing.notification.publisher;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.ticketing.common.config.RabbitMQConfig;
import com.ticketing.notification.event.BookingConfirmedEvent;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.event.TicketGenerationEvent;

@ExtendWith(MockitoExtension.class)
class BookingEventPublisherTest {

    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks private BookingEventPublisher publisher;

    @Test
    @DisplayName("publishBookingConfirmation: should send to booking exchange with routing key booking.confirmed")
    void publishBookingConfirmation_shouldSendToCorrectExchangeAndRoutingKey() {
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(1L)
                .correlationId("corr-123")
                .build();

        publisher.publishBookingConfirmation(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE, "booking.confirmed", event);
    }

    @Test
    @DisplayName("publishEmailNotification: should send to notification exchange with routing key email.send")
    void publishEmailNotification_shouldSendToCorrectExchangeAndRoutingKey() {
        EmailNotificationEvent event = EmailNotificationEvent.builder()
                .to("user@test.com")
                .subject("Test")
                .body("Hello")
                .templateType("CONFIRMATION")
                .correlationId("corr-456")
                .build();

        publisher.publishEmailNotification(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE, "email.send", event);
    }

    @Test
    @DisplayName("publishTicketGeneration: should send to booking exchange with routing key ticket.generate")
    void publishTicketGeneration_shouldSendToCorrectExchangeAndRoutingKey() {
        TicketGenerationEvent event = TicketGenerationEvent.builder()
                .bookingId(5L)
                .ticketIds(List.of(101L, 102L))
                .correlationId("corr-789")
                .build();

        publisher.publishTicketGeneration(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE, "ticket.generate", event);
    }
}
