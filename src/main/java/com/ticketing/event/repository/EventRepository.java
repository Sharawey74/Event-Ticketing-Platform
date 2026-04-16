package com.ticketing.event.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
        SELECT e
        FROM Event e
        JOIN FETCH e.organizer o
        JOIN FETCH e.category c
        JOIN FETCH e.venue v
        WHERE e.id = :id
        """)
    Optional<Event> findByIdWithDetails(@Param("id") Long id);

    Page<Event> findByStatusAndStartDateAfter(EventStatus status, Instant date, Pageable pageable);

    @Query("""
        SELECT e
        FROM Event e
        JOIN e.venue v
        WHERE e.status = :status
          AND (:categoryId IS NULL OR e.category.id = :categoryId)
          AND (:city IS NULL OR v.city = :city)
        """)
    Page<Event> findByStatusAndCategoryIdAndVenueCity(
        @Param("status") EventStatus status,
        @Param("categoryId") Long categoryId,
        @Param("city") String city,
        Pageable pageable);
}
