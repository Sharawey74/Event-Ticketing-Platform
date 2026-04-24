package com.ticketing.common.util;

public final class BusinessConstants {

    private BusinessConstants() {
    }

    public static final long RESERVATION_TTL_SECONDS = 300L;
    public static final long LOCK_TTL_SECONDS = 300L;

    public static final int EARLY_BIRD_DAYS_THRESHOLD = 30;
    public static final double EARLY_BIRD_DISCOUNT = 0.50;

    public static final int GROUP_DISCOUNT_MIN_QUANTITY = 5;
    public static final double GROUP_DISCOUNT_RATE = 0.10;

    public static final double DYNAMIC_PRICING_THRESHOLD = 0.80;
    public static final double DYNAMIC_PRICING_SURGE = 0.25;

    public static final int FULL_REFUND_DAYS_THRESHOLD = 7;
    public static final int PARTIAL_REFUND_DAYS_THRESHOLD = 3;
    public static final double PARTIAL_REFUND_RATE = 0.50;

    public static final int EXPIRY_JOB_INTERVAL_MS = 30_000;
    public static final int MAX_SEARCH_PARAM_LENGTH = 100;
}
