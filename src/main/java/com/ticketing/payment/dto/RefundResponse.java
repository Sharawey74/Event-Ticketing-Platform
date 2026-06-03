package com.ticketing.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ticketing.payment.model.RefundStatus;

import lombok.Builder;
import lombok.Getter;

/**
 * Response DTO for refund operations.
 * Never expose the Refund JPA entity directly through the API boundary.
 */
@Getter
@Builder
public class RefundResponse {

    private Long refundId;
    private Long bookingId;
    private BigDecimal amount;
    private RefundStatus status;

    /** Human-readable denial reason — non-null only when status=DENIED. */
    private String refundDenialReason;

    private Instant requestedAt;
    private Instant processedAt;
}
