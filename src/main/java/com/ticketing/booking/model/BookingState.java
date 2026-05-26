package com.ticketing.booking.model;

/**
 * All valid states a Booking can be in throughout its lifecycle.
 *
 * STATE MACHINE DESIGN DECISIONS (Audit Fix A.3):
 * - AVAILABLE is NOT a booking state. A booking is created in RESERVED state.
 *   Seat availability is tracked by TicketTier.availableCount (DB) and Redis (cache).
 * - RELEASED is NOT a booking state. When a booking expires or payment fails,
 *   seats are released back to inventory via ReleaseSeatsAction. The booking
 *   terminal state is EXPIRED or PAYMENT_FAILED — not RELEASED.
 * - CANCELLED is a terminal state for organizer-triggered event cancellations (Fix 11.1).
 *   It is reachable from CONFIRMED, RESERVED, and PAYMENT_PENDING.
 *
 * FULL STATE FLOW:
 *   RESERVED → PAYMENT_PENDING → CONFIRMED → ATTENDED (success path)
 *   RESERVED → EXPIRED (timer expired — no payment made)
 *   PAYMENT_PENDING → PAYMENT_FAILED (payment declined)
 *   CONFIRMED → REFUND_REQUESTED → REFUND_APPROVED (user cancels 7+ days before event)
 *   CONFIRMED → REFUND_REQUESTED → REFUND_DENIED (user cancels <3 days before event)
 *   CONFIRMED / RESERVED / PAYMENT_PENDING → CANCELLED (organizer cancels the event)
 */
public enum BookingState {
    RESERVED,          // Seats held in Redis, 5-min countdown started
    PAYMENT_PENDING,   // User proceeded to checkout; Stripe session created
    CONFIRMED,         // Payment succeeded; tickets generated
    ATTENDED,          // QR code scanned at venue; ticket used
    EXPIRED,           // Reservation timed out before payment; seats released
    PAYMENT_FAILED,    // Stripe payment declined; seats released
    REFUND_REQUESTED,  // User requested a refund from CONFIRMED booking
    REFUND_APPROVED,   // Admin approved refund; Stripe refund initiated
    REFUND_DENIED,     // Admin denied refund (< 3 days before event)
    CANCELLED          // Fix 11.1: Organizer cancelled the event; full refund for CONFIRMED bookings
}
