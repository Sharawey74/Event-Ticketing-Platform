package com.ticketing.booking.model;

public enum BookingEvent {
    PROCEED_TO_CHECKOUT,
    PAYMENT_SUCCESS,
    PAYMENT_FAILURE,
    EXPIRE_RESERVATION,
    REQUEST_REFUND,
    APPROVE_REFUND,
    DENY_REFUND,
    EVENT_CANCELLED,
    CHECK_IN
}
