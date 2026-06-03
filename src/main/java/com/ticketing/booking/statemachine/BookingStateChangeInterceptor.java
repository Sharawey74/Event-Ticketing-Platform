package com.ticketing.booking.statemachine;

import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingStateChangeInterceptor extends StateMachineInterceptorAdapter<BookingState, BookingEvent> {

    private final BookingRepository bookingRepository;

    @Override
    public void preStateChange(State<BookingState, BookingEvent> state, Message<BookingEvent> message,
            Transition<BookingState, BookingEvent> transition, StateMachine<BookingState, BookingEvent> stateMachine,
            StateMachine<BookingState, BookingEvent> rootStateMachine) {

        Object bookingIdObj = stateMachine.getExtendedState().getVariables().get("bookingId");
        if (bookingIdObj instanceof Long bookingId) {
            bookingRepository.findById(bookingId).ifPresent(booking -> {
                log.info("Interceptor updating booking {} state to {}", bookingId, state.getId());
                booking.setState(state.getId());
                bookingRepository.save(booking);
            });
        }
    }
}
