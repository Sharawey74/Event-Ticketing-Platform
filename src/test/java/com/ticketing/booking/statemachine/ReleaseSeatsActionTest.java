package com.ticketing.booking.statemachine;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.ExtendedState;
import org.springframework.statemachine.StateContext;

import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.waitlist.service.WaitlistService;

@ExtendWith(MockitoExtension.class)
class ReleaseSeatsActionTest {

    @Mock private WaitlistService waitlistService;
    @Mock private StateContext<BookingState, BookingEvent> context;
    @Mock private ExtendedState extendedState;

    @InjectMocks private ReleaseSeatsAction action;

    private Map<Object, Object> variables;

    @BeforeEach
    void setUp() {
        variables = new HashMap<>();
        when(context.getExtendedState()).thenReturn(extendedState);
        when(extendedState.getVariables()).thenReturn(variables);
    }

    @Test
    @DisplayName("execute: should notify waitlist when tierId and quantity are present")
    void execute_whenTierIdAndQuantityPresent_shouldNotifyWaitlist() {
        variables.put("bookingId", 20L);
        variables.put("tierId", 50L);
        variables.put("quantity", 2);

        action.execute(context);

        verify(waitlistService).notifyWaitlist(50L, 2);
    }

    @Test
    @DisplayName("execute: should skip waitlist notification when tierId is null")
    void execute_whenTierIdNull_shouldSkipWaitlistNotification() {
        variables.put("bookingId", 20L);
        variables.put("quantity", 2);

        action.execute(context);

        verify(waitlistService, never()).notifyWaitlist(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("execute: should skip waitlist notification when quantity is zero")
    void execute_whenQuantityZero_shouldSkipWaitlistNotification() {
        variables.put("bookingId", 20L);
        variables.put("tierId", 50L);
        variables.put("quantity", 0);

        action.execute(context);

        verify(waitlistService, never()).notifyWaitlist(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
