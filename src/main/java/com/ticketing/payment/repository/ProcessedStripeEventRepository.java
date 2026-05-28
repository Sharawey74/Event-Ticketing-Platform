package com.ticketing.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ticketing.payment.model.ProcessedStripeEvent;

/**
 * Fix 9.2: Used to INSERT a record for each Stripe event we process.
 * The UNIQUE constraint on stripe_event_id means concurrent duplicate deliveries
 * will cause DataIntegrityViolationException on the second insert — which is caught
 * and silently ignored in WebhookService.processEvent().
 */
@Repository
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, Long> {

    Optional<ProcessedStripeEvent> findByStripeEventId(String stripeEventId);
}
