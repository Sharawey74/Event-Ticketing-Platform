package com.ticketing.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class RabbitMQConfig {

    public static final String BOOKING_EXCHANGE = "booking.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";

    public static final String BOOKING_CONFIRMATION_QUEUE = "booking.confirmation.queue";
    public static final String BOOKING_CONFIRMATION_DLQ = "booking.confirmation.dlq";

    public static final String EMAIL_NOTIFICATION_QUEUE = "email.notification.queue";
    public static final String EMAIL_NOTIFICATION_DLQ = "email.notification.dlq";

    public static final String TICKET_GENERATION_QUEUE = "ticket.generation.queue";
    public static final String TICKET_GENERATION_DLQ = "ticket.generation.dlq";

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }

    // Exchanges
    @Bean
    public TopicExchange bookingExchange() {
        return new TopicExchange(BOOKING_EXCHANGE);
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    // Booking Confirmation Queues
    @Bean
    public Queue bookingConfirmationDlq() {
        return new Queue(BOOKING_CONFIRMATION_DLQ);
    }

    @Bean
    public Queue bookingConfirmationQueue() {
        return QueueBuilder.durable(BOOKING_CONFIRMATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", BOOKING_CONFIRMATION_DLQ)
                .build();
    }

    @Bean
    public Binding bindingBookingConfirmation(Queue bookingConfirmationQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingConfirmationQueue).to(bookingExchange).with("booking.confirmed");
    }

    // Email Notification Queues
    @Bean
    public Queue emailNotificationDlq() {
        return new Queue(EMAIL_NOTIFICATION_DLQ);
    }

    @Bean
    public Queue emailNotificationQueue() {
        return QueueBuilder.durable(EMAIL_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", EMAIL_NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Binding bindingEmailNotification(Queue emailNotificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(emailNotificationQueue).to(notificationExchange).with("email.send");
    }

    // Ticket Generation Queues (Fix 10.2)
    @Bean
    public Queue ticketGenerationDlq() {
        return new Queue(TICKET_GENERATION_DLQ);
    }

    @Bean
    public Queue ticketGenerationQueue() {
        return QueueBuilder.durable(TICKET_GENERATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", TICKET_GENERATION_DLQ)
                .build();
    }

    @Bean
    public Binding bindingTicketGeneration(Queue ticketGenerationQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(ticketGenerationQueue).to(bookingExchange).with("ticket.generate");
    }
}
