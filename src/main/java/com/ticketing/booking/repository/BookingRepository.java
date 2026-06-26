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
