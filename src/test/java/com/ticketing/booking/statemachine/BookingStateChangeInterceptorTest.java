package com.ticketing.booking.statemachine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.transition.Transition;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;

@ExtendWith(MockitoExtension.class)
class BookingStateChangeInterceptorTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private StateMachine<BookingState, BookingEvent> stateMachine;
    @Mock private StateMachine<BookingState, BookingEvent> rootStateMachine;
    @Mock private State<BookingState, BookingEvent> state;
    @Mock private Message<BookingEvent> message;
    @Mock private Transition<BookingState, BookingEvent> transition;
    @Mock private ExtendedState extendedState;

    @InjectMocks private BookingStateChangeInterceptor interceptor;

    private Map<Object, Object> variables;

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        when(stateMachine.getExtendedState()).thenReturn(extendedState);
        when(extendedState.getVariables()).thenReturn(variables);
    }

    @Test
    @DisplayName("preStateChange: should update and save booking state when bookingId is present")
    void preStateChange_whenBookingExists_shouldUpdateStateAndSave() {
        variables.put("bookingId", 42L);
        Booking booking = Booking.builder().id(42L).state(BookingState.RESERVED).build();
        when(state.getId()).thenReturn(BookingState.CONFIRMED);
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        interceptor.preStateChange(state, message, transition, stateMachine, rootStateMachine);

        verify(bookingRepository).save(booking);
    }

    @Test
    @DisplayName("preStateChange: should do nothing when booking is not found")
    void preStateChange_whenBookingNotFound_shouldDoNothing() {
        variables.put("bookingId", 99L);
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        interceptor.preStateChange(state, message, transition, stateMachine, rootStateMachine);

        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("preStateChange: should do nothing when bookingId variable is absent")
    void preStateChange_whenBookingIdAbsent_shouldDoNothing() {
        interceptor.preStateChange(state, message, transition, stateMachine, rootStateMachine);

        verify(bookingRepository, never()).findById(any());
        verify(bookingRepository, never()).save(any());
    }
}
