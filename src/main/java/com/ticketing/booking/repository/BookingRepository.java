package com.ticketing.booking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.booking.model.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = { "user", "event" })
    Page<Booking> findAll(Pageable pageable);
}
