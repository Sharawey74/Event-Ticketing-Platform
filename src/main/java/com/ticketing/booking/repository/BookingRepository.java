package com.ticketing.booking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = { "user", "event" })
    Page<Booking> findAll(Pageable pageable);

    @EntityGraph(attributePaths = { "user", "event", "event.venue", "event.category" })
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "user", "event", "event.venue", "event.category", "tickets", "tickets.tier" })
    @org.springframework.data.jpa.repository.Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") Long id);

    // BUG-03 Fix: Required by ReservationExpirationJob on Day 8
    List<Booking> findByStateAndExpiresAtBefore(BookingState state, Instant time);

    /**
     * Fix 26-idem: looks up the booking a given Idempotency-Key already created.
     *
     * <p>Used twice by {@code BookingIdempotencyService} — once as a fast path before reserving,
     * and again after a {@code DataIntegrityViolationException} to find the row that won the race.
     *
     * <p>Note this inherits {@code @SQLRestriction("deleted_at IS NULL")} from the entity, so a
     * soft-deleted booking is invisible here even though its key still occupies the UNIQUE index.
     * Nothing soft-deletes bookings today (cancellation sets state, not {@code deleted_at}); if
     * that ever changes, a replayed key would surface as a 500 rather than a replay.
     */
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    // Recovery: ReservationExpirationJob expires stale RESERVED and PAYMENT_PENDING holds.
    // Tickets + tier are fetched so inventory can be released without a lazy-load round trip.
    @EntityGraph(attributePaths = { "tickets", "tickets.tier" })
    List<Booking> findByStateInAndExpiresAtBefore(java.util.Collection<BookingState> states, Instant time);

    @Query("""
            SELECT b.event.id AS eventId, COUNT(tk) AS ticketsSold, SUM(b.totalAmount) AS grossRevenue
            FROM Booking b JOIN b.tickets tk
            WHERE b.event.id IN :eventIds
            AND b.state IN (
                com.ticketing.booking.model.BookingState.CONFIRMED,
                com.ticketing.booking.model.BookingState.ATTENDED
            )
            GROUP BY b.event.id
            """)
    List<EventStatsProjection> findEventStats(@Param("eventIds") List<Long> eventIds);

    // BUG-03 Fix: Pessimistic write lock for state machine transitions
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") Long id);

    @EntityGraph(attributePaths = { "user", "tickets", "tickets.tier" })
    @Query("SELECT b FROM Booking b WHERE b.event.id = :eventId AND b.state IN (com.ticketing.booking.model.BookingState.CONFIRMED, com.ticketing.booking.model.BookingState.ATTENDED)")
    List<Booking> findConfirmedByEventId(@Param("eventId") Long eventId);
}
