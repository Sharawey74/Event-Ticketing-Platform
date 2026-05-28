package com.ticketing.payment.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.stripe.Stripe;

/**
 * Initializes the Stripe SDK global API key on application startup.
 * The key is injected from environment variable STRIPE_SECRET_KEY.
 * Never hardcode a stripe key — always use ${STRIPE_SECRET_KEY}.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("[stripe-config] Stripe SDK initialized. Key prefix: {}",
                secretKey.length() > 7 ? secretKey.substring(0, 7) + "..." : "INVALID");
    }
}
