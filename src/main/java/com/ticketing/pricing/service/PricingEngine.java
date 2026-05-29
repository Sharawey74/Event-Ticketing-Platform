package com.ticketing.pricing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.ticketing.common.util.BusinessConstants;

/**
 * Pure pricing engine — zero DB calls, zero external service calls.
 * All thresholds and rates come from BusinessConstants (Fix CC-2).
 *
 * Three pricing rules, all ADDITIVE:
 *   1. Early Bird  — 50% off if event is ≥ 30 days away
 *   2. Group       — 10% off for 5+ tickets
 *   3. Dynamic Surge — 25% markup if occupancy ≥ 80%
 *
 * Rules are applied in order: early bird → group → surge.
 * Early bird and group discounts can both apply to the same booking.
 */
@Service
public class PricingEngine {

    /**
     * Rule 1: Early Bird Discount.
     * Applies 50% off if the event is ≥ EARLY_BIRD_DAYS_THRESHOLD (30) days away.
     *
     * @param basePrice the original ticket price
     * @param eventDate the event start date (Instant)
     * @return discounted price, or basePrice if threshold not met
     */
    public BigDecimal applyEarlyBirdDiscount(BigDecimal basePrice, Instant eventDate) {
        long daysUntilEvent = ChronoUnit.DAYS.between(Instant.now(), eventDate);
        if (daysUntilEvent >= BusinessConstants.EARLY_BIRD_DAYS_THRESHOLD) {
            return basePrice.multiply(BigDecimal.valueOf(1.0 - BusinessConstants.EARLY_BIRD_DISCOUNT));
        }
        return basePrice;
    }

    /**
     * Rule 2: Group Discount.
     * Applies 10% off for GROUP_DISCOUNT_MIN_QUANTITY (5) or more tickets.
     *
     * @param price    price after any prior discounts
     * @param quantity number of tickets being purchased
     * @return discounted price, or price unchanged if threshold not met
     */
    public BigDecimal applyGroupDiscount(BigDecimal price, int quantity) {
        if (quantity >= BusinessConstants.GROUP_DISCOUNT_MIN_QUANTITY) {
            return price.multiply(BigDecimal.valueOf(1.0 - BusinessConstants.GROUP_DISCOUNT_RATE));
        }
        return price;
    }

    /**
     * Rule 3: Dynamic Surge Pricing.
     * Applies 25% markup if occupancy (sold/capacity) ≥ DYNAMIC_PRICING_THRESHOLD (0.80).
     *
     * @param price    price after prior discounts
     * @param sold     number of seats already sold
     * @param capacity total tier capacity
     * @return surged price, or price unchanged if threshold not met
     */
    public BigDecimal applyDynamicPricing(BigDecimal price, int sold, int capacity) {
        if (capacity <= 0) {
            return price;
        }
        double occupancy = (double) sold / capacity;
        if (occupancy >= BusinessConstants.DYNAMIC_PRICING_THRESHOLD) {
            return price.multiply(BigDecimal.valueOf(1.0 + BusinessConstants.DYNAMIC_PRICING_SURGE));
        }
        return price;
    }

    /**
     * Calculates the final price by applying all three rules in sequence.
     * Order: early bird → group → surge.
     * Result is rounded to 2 decimal places (HALF_UP).
     *
     * @param basePrice base price per ticket
     * @param eventDate the event start date
     * @param quantity  number of tickets
     * @param sold      current seats sold in this tier
     * @param capacity  total tier capacity
     * @return final price per ticket after all applicable rules
     */
    public BigDecimal calculateFinalPrice(BigDecimal basePrice, Instant eventDate,
                                          int quantity, int sold, int capacity) {
        BigDecimal price = applyEarlyBirdDiscount(basePrice, eventDate);
        price = applyGroupDiscount(price, quantity);
        price = applyDynamicPricing(price, sold, capacity);
        return price.setScale(2, RoundingMode.HALF_UP);
    }
}
