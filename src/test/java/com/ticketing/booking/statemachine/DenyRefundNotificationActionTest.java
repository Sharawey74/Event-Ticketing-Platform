package com.ticketing.booking.statemachine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateContext;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.notification.event.EmailNotificationEvent;
import com.ticketing.notification.publisher.BookingEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DenyRefundNotificationActionTest {

    @Mock private BookingEventPublisher publisher;
    @Mock private StateContext<BookingState, BookingEvent> context;
    @Mock private ExtendedState extendedState;

    @InjectMocks private DenyRefundNotificationAction action;

    private Map<Object, Object> variables;

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        when(context.getExtendedState()).thenReturn(extendedState);
        when(extendedState.getVariables()).thenReturn(variables);
    }

    @Test
    @DisplayName("execute: should publish EmailNotificationEvent with denial details")
    void execute_whenAllVariablesPresent_shouldPublishEmailNotification() {
        variables.put("bookingId", 42L);
        variables.put("denialReason", "Event is too close");
        variables.put("userEmail", "user@test.com");

        ArgumentCaptor<EmailNotificationEvent> captor =
                ArgumentCaptor.forClass(EmailNotificationEvent.class);

        action.execute(context);

        verify(publisher).publishEmailNotification(captor.capture());
        EmailNotificationEvent event = captor.getValue();
        assertThat(event.getTo()).isEqualTo("user@test.com");
        assertThat(event.getSubject()).contains("denied");
        assertThat(event.getBody()).contains("42");
        assertThat(event.getBody()).contains("Event is too close");
        assertThat(event.getTemplateType()).isEqualTo("REFUND_DENIED");
    }

    @Test
    @DisplayName("execute: should use fallback text when denial reason is null")
    void execute_whenReasonNull_shouldUseFallbackText() {
        variables.put("bookingId", 10L);
        variables.put("userEmail", "user@test.com");

        ArgumentCaptor<EmailNotificationEvent> captor =
                ArgumentCaptor.forClass(EmailNotificationEvent.class);

        action.execute(context);

        verify(publisher).publishEmailNotification(captor.capture());
        assertThat(captor.getValue().getBody()).contains("refund policy");
    }

    @Test
    @DisplayName("execute: should send to empty string when userEmail is null")
    void execute_whenUserEmailNull_shouldSendToEmptyString() {
        variables.put("bookingId", 5L);

        action.execute(context);

        ArgumentCaptor<EmailNotificationEvent> captor =
                ArgumentCaptor.forClass(EmailNotificationEvent.class);
        verify(publisher).publishEmailNotification(captor.capture());
        assertThat(captor.getValue().getTo()).isEmpty();
    }
}
