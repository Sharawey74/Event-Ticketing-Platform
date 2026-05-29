package com.ticketing.booking.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.guard.Guard;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;

import reactor.core.publisher.Mono;

@SpringBootTest(classes = BookingStateMachineTest.TestConfig.class)
class BookingStateMachineTest {

    @Configuration
    @Import(BookingStateMachineConfig.class)
    static class TestConfig {
    }

    @Autowired
    private StateMachineFactory<BookingState, BookingEvent> factory;

    @MockitoBean(name = "confirmBookingAction")
    private Action<BookingState, BookingEvent> confirmBookingAction;

    @MockitoBean(name = "releaseSeatsAction")
    private Action<BookingState, BookingEvent> releaseSeatsAction;

    @MockitoBean(name = "denyRefundNotificationAction")
    private Action<BookingState, BookingEvent> denyRefundNotificationAction;

    @MockitoBean(name = "cancelBookingAction")
    private Action<BookingState, BookingEvent> cancelBookingAction;

    @MockitoBean(name = "checkInGuard")
    private Guard<BookingState, BookingEvent> checkInGuard;

    @Test
    void transition_fromReservedToPaymentPending_shouldAdvanceState() {
        StateMachine<BookingState, BookingEvent> stateMachine = factory.getStateMachine();
        stateMachine.startReactively().block();

        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PROCEED_TO_CHECKOUT).build())).blockLast();

        assertThat(stateMachine.getState().getId()).isEqualTo(BookingState.PAYMENT_PENDING);
    }

    @Test
    void transition_fromReservedToExpired_afterFiveMinutes_shouldReleaseSeats() {
        StateMachine<BookingState, BookingEvent> stateMachine = factory.getStateMachine();
        stateMachine.startReactively().block();

        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.EXPIRE_RESERVATION).build())).blockLast();

        assertThat(stateMachine.getState().getId()).isEqualTo(BookingState.EXPIRED);
        verify(releaseSeatsAction).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transition_fromPaymentPendingToConfirmed_shouldGenerateQRCode() {
        StateMachine<BookingState, BookingEvent> stateMachine = factory.getStateMachine();
        stateMachine.startReactively().block();

        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PROCEED_TO_CHECKOUT).build())).blockLast();
        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PAYMENT_SUCCESS).build())).blockLast();

        assertThat(stateMachine.getState().getId()).isEqualTo(BookingState.CONFIRMED);
        verify(confirmBookingAction).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transition_toConfirmed_shouldFireConfirmBookingAction() {
        StateMachine<BookingState, BookingEvent> stateMachine = factory.getStateMachine();
        stateMachine.startReactively().block();

        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PROCEED_TO_CHECKOUT).build())).blockLast();
        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PAYMENT_SUCCESS).build())).blockLast();

        assertThat(stateMachine.getState().getId()).isEqualTo(BookingState.CONFIRMED);
        verify(confirmBookingAction).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidTransition_fromConfirmedToReserved_shouldThrowException() {
        StateMachine<BookingState, BookingEvent> stateMachine = factory.getStateMachine();
        stateMachine.startReactively().block();

        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PROCEED_TO_CHECKOUT).build())).blockLast();
        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PAYMENT_SUCCESS).build())).blockLast();

        assertThat(stateMachine.getState().getId()).isEqualTo(BookingState.CONFIRMED);

        // Invalid transition should return an event result that is rejected, state remains unchanged.
        var result = stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(BookingEvent.PROCEED_TO_CHECKOUT).build())).blockLast();
        
        assertThat(result.getResultType().name()).isEqualTo("DENIED");
        assertThat(stateMachine.getState().getId()).isEqualTo(BookingState.CONFIRMED);
    }
}
