package com.ticketing.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.booking.model.TicketTier;

public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {

    // Day 21 concurrency hardening: a single atomic conditional UPDATE, not a JPA
    // entity read-modify-write. A tier.setAvailableCount()+save() approach raced on the
    // entity's @Version under concurrent different-user reservations for the same tier —
    // this has no read-then-write gap for the optimistic lock to catch a conflict on.
    @Modifying
    @Query("UPDATE TicketTier t SET t.availableCount = t.availableCount - :quantity "
            + "WHERE t.id = :id AND t.availableCount >= :quantity")
    int decrementAvailableCount(@Param("id") Long id, @Param("quantity") int quantity);
}
