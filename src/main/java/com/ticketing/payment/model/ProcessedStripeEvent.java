package com.ticketing.payment.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stores every Stripe event ID we have successfully processed.
 *
 * Fix 9.2 — CRITICAL: The UNIQUE constraint on stripe_event_id is the real idempotency guard.
 * If Stripe delivers the same event twice concurrently, both threads pass the existsBy() check
 * simultaneously and both try to INSERT. The second INSERT hits the unique constraint and throws
 * DataIntegrityViolationException — which WebhookService catches and silently ignores.
 * This eliminates duplicate booking confirmations under concurrent Stripe retries.
 *
 * Schema: see V6__create_payments_and_refunds.sql
 *   CONSTRAINT uq_processed_stripe_events_event_id UNIQUE (stripe_event_id)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_stripe_events")
public class ProcessedStripeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false, unique = true, length = 255)
    private String stripeEventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }
}
