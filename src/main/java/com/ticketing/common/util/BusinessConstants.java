package com.ticketing.common.util;

public final class BusinessConstants {

    private BusinessConstants() {
    }

    public static final long RESERVATION_TTL_SECONDS = 300L;
    public static final long LOCK_TTL_SECONDS = 300L;

    // Stripe Checkout Session lifetime (Stripe requires >= 30 min). Once a booking enters
    // PAYMENT_PENDING the reservation hold is extended to this value so it matches the
    // Stripe session expiry and can be safely auto-expired afterwards.
    public static final long STRIPE_SESSION_TTL_SECONDS = 1860L; // 31 minutes

    public static final int EARLY_BIRD_DAYS_THRESHOLD = 30;
    public static final double EARLY_BIRD_DISCOUNT = 0.50;

    public static final int GROUP_DISCOUNT_MIN_QUANTITY = 5;
    public static final double GROUP_DISCOUNT_RATE = 0.10;

    public static final double DYNAMIC_PRICING_THRESHOLD = 0.80;
    public static final double DYNAMIC_PRICING_SURGE = 0.25;

    public static final int FULL_REFUND_DAYS_THRESHOLD = 7;
    public static final int PARTIAL_REFUND_DAYS_THRESHOLD = 3;
    public static final double PARTIAL_REFUND_RATE = 0.50;

    public static final long EXPIRY_JOB_INTERVAL_MS = 30_000L;
    public static final int MAX_SEARCH_PARAM_LENGTH = 100;

    // Ticket tier limits
    public static final int MAX_TICKETS_PER_BOOKING = 10; // Default max tickets per single booking

    // QR Code generation (ZXing) — Fix CC-2
    public static final int QR_CODE_SIZE   = 300; // pixels (width and height)
    public static final int QR_CODE_MARGIN = 1;   // quiet zone modules around QR pattern
}
