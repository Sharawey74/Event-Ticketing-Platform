package com.ticketing.booking.model;

public enum BookingState {
    AVAILABLE,
    RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    ATTENDED,
    EXPIRED,
    RELEASED,
    PAYMENT_FAILED,
    REFUND_REQUESTED,
    REFUND_APPROVED,
    REFUND_DENIED
}
