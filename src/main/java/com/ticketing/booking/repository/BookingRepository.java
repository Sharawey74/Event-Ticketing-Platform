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

    // BUG-03 Fix: Required by ReservationExpirationJob on Day 8
    List<Booking> findByStateAndExpiresAtBefore(BookingState state, Instant time);

    // BUG-03 Fix: Pessimistic write lock for state machine transitions
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithLock(@Param("id") Long id);
}
