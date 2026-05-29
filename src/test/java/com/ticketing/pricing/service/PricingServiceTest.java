package com.ticketing.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TDD Red Phase — Day 11
 *
 * Tests for PricingEngine (pure unit tests — zero Spring context, zero DB, zero Redis).
 *
 * Non-negotiable rules enforced here:
 * - ALL thresholds and rates come from BusinessConstants (Fix CC-2).
 * - ChronoUnit.DAYS.between() for all day-distance calculations.
 * - Pricing rules are ADDITIVE — early bird + group discount both apply simultaneously.
 */
class PricingServiceTest {

    private PricingEngine pricingEngine;

    @BeforeEach
    void setUp() {
        pricingEngine = new PricingEngine();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 1 — Early Bird: 50% off when event is ≥ 30 days away
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyEarlyBirdDiscount: event ≥ 30 days away → 50% discount applied")
    void applyPricing_whenEventIsMoreThan30DaysAway_shouldApply50PercentEarlyBirdDiscount() {
        // Arrange
        BigDecimal basePrice = new BigDecimal("100.00");
        Instant eventDate = Instant.now().plus(35, ChronoUnit.DAYS);

        // Act
        BigDecimal result = pricingEngine.applyEarlyBirdDiscount(basePrice, eventDate);

        // Assert — 100 * (1 - 0.50) = 50.00
        assertThat(result).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("applyEarlyBirdDiscount: event < 30 days away → no discount")
    void applyPricing_whenEventIsLessThan30DaysAway_shouldNotApplyEarlyBirdDiscount() {
        // Arrange
        BigDecimal basePrice = new BigDecimal("100.00");
        Instant eventDate = Instant.now().plus(15, ChronoUnit.DAYS);

        // Act
        BigDecimal result = pricingEngine.applyEarlyBirdDiscount(basePrice, eventDate);

        // Assert — no discount applied
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 2 — Group Discount: 10% off for 5+ tickets
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyGroupDiscount: quantity ≥ 5 → 10% discount applied")
    void applyPricing_whenQuantityIs5OrMore_shouldApply10PercentGroupDiscount() {
        // Arrange
        BigDecimal price = new BigDecimal("100.00");
        int quantity = 5;

        // Act
        BigDecimal result = pricingEngine.applyGroupDiscount(price, quantity);

        // Assert — 100 * (1 - 0.10) = 90.00
        assertThat(result).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("applyGroupDiscount: quantity < 5 → no discount")
    void applyPricing_whenQuantityIsLessThan5_shouldNotApplyGroupDiscount() {
        // Arrange
        BigDecimal price = new BigDecimal("100.00");
        int quantity = 4;

        // Act
        BigDecimal result = pricingEngine.applyGroupDiscount(price, quantity);

        // Assert — no discount
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule 3 — Dynamic Surge: 25% markup when occupancy ≥ 80%
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("applyDynamicPricing: occupancy ≥ 80% → 25% surge markup applied")
    void applyPricing_whenInventoryAbove80Percent_shouldApply25PercentSurge() {
        // Arrange
        BigDecimal price = new BigDecimal("100.00");
        int sold     = 80;
        int capacity = 100; // 80% sold

        // Act
        BigDecimal result = pricingEngine.applyDynamicPricing(price, sold, capacity);

        // Assert — 100 * (1 + 0.25) = 125.00
        assertThat(result).isEqualByComparingTo(new BigDecimal("125.00"));
    }

    @Test
    @DisplayName("applyDynamicPricing: occupancy < 80% → no surge")
    void applyPricing_whenInventoryBelow80Percent_shouldNotApplySurge() {
        // Arrange
        BigDecimal price = new BigDecimal("100.00");
        int sold     = 70;
        int capacity = 100; // 70%

        // Act
        BigDecimal result = pricingEngine.applyDynamicPricing(price, sold, capacity);

        // Assert — no surge
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Additive: early bird + group both apply
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("calculateFinalPrice: early bird AND group discount both apply (additive)")
    void calculateFinalPrice_withEarlyBirdAndGroupDiscount_shouldApplyBoth() {
        // Arrange
        BigDecimal basePrice = new BigDecimal("100.00");
        Instant eventDate = Instant.now().plus(40, ChronoUnit.DAYS); // triggers early bird
        int quantity = 6;   // triggers group discount
        int sold     = 50;
        int capacity = 100; // 50% — no surge

        // Act
        BigDecimal result = pricingEngine.calculateFinalPrice(basePrice, eventDate, quantity, sold, capacity);

        // Assert
        // Step 1: earlyBird → 100 * 0.50 = 50.00
        // Step 2: group    → 50 * 0.90  = 45.00
        // Step 3: surge    → no surge (50% occupancy)
        assertThat(result).isEqualByComparingTo(new BigDecimal("45.00"));
    }
}
