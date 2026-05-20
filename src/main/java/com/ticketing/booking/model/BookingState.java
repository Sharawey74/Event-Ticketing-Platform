package com.ticketing.booking.model;

public enum BookingState {
    RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    ATTENDED,
    EXPIRED,
    PAYMENT_FAILED,
    REFUND_REQUESTED,
    REFUND_APPROVED,
    REFUND_DENIED
}
