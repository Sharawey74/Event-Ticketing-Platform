package com.ticketing.waitlist.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a user's place in the waitlist for a specific ticket tier.
 *
 * Business rules:
 * - A user can only join the waitlist for a given tier once (UNIQUE user_id + tier_id).
 * - Entries are ordered by created_at ASC — first-come, first-served.
 * - When seats are released (EXPIRED or REFUND_APPROVED), the top N entries are notified.
 *
 * Schema: V11__add_waitlist_and_refund_reason.sql
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "waitlist_entries",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_waitlist_user_tier",
        columnNames = {"user_id", "tier_id"}
    )
)
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tier_id", nullable = false)
    private Long tierId;

    /**
     * Denormalized for email sending — avoids a JOIN to the users table in the consumer.
     * Updated if a user changes their email (handled via UserService).
     */
    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
