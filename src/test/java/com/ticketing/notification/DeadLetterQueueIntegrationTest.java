package com.ticketing.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketing.booking.repository.TicketRepository;
import com.ticketing.common.config.RabbitMQConfig;
import com.ticketing.notification.event.TicketGenerationEvent;
import com.ticketing.ticketing_platform.TestcontainersConfiguration;

import org.awaitility.Awaitility;

/**
 * Fix 26-dlq — proves a message that cannot be processed reaches its dead-letter queue.
 *
 * <p>Before this fix the DLQs were declared and bound but unreachable. Dead-lettering happens only
 * when a message is rejected <em>without requeue</em>, and Spring Boot defaults to
 * {@code default-requeue-rejected=true} with retry disabled — so a listener that threw had its
 * message requeued immediately and indefinitely.
 *
 * <p><b>Choosing a failure mode is the whole difficulty of this test.</b> The first version of it
 * published an unconvertible payload and passed against the <em>broken</em> configuration, proving
 * nothing: Spring AMQP's default {@code ConditionalRejectingErrorHandler} classifies a
 * {@code MessageConversionException} as <b>fatal</b> and rejects it without requeue regardless of
 * {@code default-requeue-rejected}. That path was already working and was never the bug.
 *
 * <p>The bug lives in the <em>non-fatal</em> path: an ordinary runtime exception thrown from inside
 * the listener body. That is what this test forces, by making the repository the listener depends
 * on throw. Verified to fail against the pre-fix configuration.
 */
@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
class DeadLetterQueueIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    /** The queue beans as this application declares them — not freshly constructed ones. */
    @Autowired
    private Queue ticketGenerationDlq;

    @Autowired
    private Queue emailNotificationDlq;

    @Autowired
    private Queue bookingConfirmationDlq;

    /**
     * Makes the ticket-generation listener throw a plain RuntimeException — the non-fatal failure
     * mode that {@code default-requeue-rejected} actually governs.
     */
    @MockitoBean
    private TicketRepository ticketRepository;

    @Test
    @DisplayName("A listener that throws has its message dead-lettered, not requeued forever")
    void listenerFailure_shouldDeadLetterInsteadOfRequeueingForever() {
        when(ticketRepository.findById(anyLong()))
                .thenThrow(new IllegalStateException("forced failure for the DLQ test"));

        rabbitAdmin.purgeQueue(RabbitMQConfig.TICKET_GENERATION_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitMQConfig.TICKET_GENERATION_DLQ, false);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.BOOKING_EXCHANGE,
                "ticket.generate",
                TicketGenerationEvent.builder()
                        .ticketIds(List.of(1L))
                        .bookingId(1L)
                        .eventId(1L)
                        .correlationId("dlq-test")
                        .build());

        // 3 attempts spaced 3s then 6s, so the reject lands ~9s in. Generous ceiling for CI.
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(depthOf(RabbitMQConfig.TICKET_GENERATION_DLQ))
                        .as("the failing message must be parked in the DLQ")
                        .isEqualTo(1));

        assertThat(depthOf(RabbitMQConfig.TICKET_GENERATION_QUEUE))
                .as("the working queue must have drained — a requeue loop leaves it non-empty")
                .isZero();
    }

    @Test
    @DisplayName("The declared dead-letter queues are durable, so a restart does not discard parked messages")
    void deadLetterQueues_shouldBeDurable() {
        // Asserted against the actual beans this app declares. A DLQ that evaporates on restart is
        // worse than none: it looks like a safety net while silently dropping the evidence.
        assertThat(ticketGenerationDlq.isDurable()).as("ticket generation DLQ").isTrue();
        assertThat(emailNotificationDlq.isDurable()).as("email notification DLQ").isTrue();
        assertThat(bookingConfirmationDlq.isDurable()).as("booking confirmation DLQ").isTrue();
    }

    private int depthOf(String queue) {
        var info = rabbitAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }
}
