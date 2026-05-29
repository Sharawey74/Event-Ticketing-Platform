package com.ticketing.waitlist.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.waitlist.model.WaitlistEntry;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    /**
     * Returns the top N waitlist entries for a given tier, ordered by created_at ASC.
     * Used by WaitlistService.notifyWaitlist() to determine who to notify next.
     *
     * @param tierId       the ticket tier ID
     * @param maxResults   number of entries to return (= releasedSeats count)
     */
    @Query("SELECT w FROM WaitlistEntry w WHERE w.tierId = :tierId ORDER BY w.createdAt ASC LIMIT :maxResults")
    List<WaitlistEntry> findTopByTierIdOrderByCreatedAtAsc(
            @Param("tierId") Long tierId,
            @Param("maxResults") int maxResults);

    boolean existsByUserIdAndTierId(Long userId, Long tierId);

    void deleteByUserIdAndTierId(Long userId, Long tierId);
}
