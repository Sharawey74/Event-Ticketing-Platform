package com.ticketing.event.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;

public interface EventRepository extends JpaRepository<Event, Long> {

    @EntityGraph(attributePaths = { "organizer", "category", "venue" })
    Page<Event> findAll(Pageable pageable);

    @Query("""
            SELECT e
            FROM Event e
            JOIN FETCH e.organizer o
            JOIN FETCH e.category c
            JOIN FETCH e.venue v
            LEFT JOIN FETCH e.ticketTiers t
            WHERE e.id = :id
            """)
    Optional<Event> findByIdWithDetails(@Param("id") Long id);

    Page<Event> findByStatusAndStartDateAfter(EventStatus status, Instant date, Pageable pageable);

    @Query("""
            SELECT e FROM Event e
            LEFT JOIN FETCH e.ticketTiers
            WHERE e.organizer.id = :organizerId
            ORDER BY e.startDate DESC
            """)
    List<Event> findByOrganizerIdWithTiers(@Param("organizerId") Long organizerId);

    @EntityGraph(attributePaths = { "organizer", "category", "venue" })
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

    @EntityGraph(attributePaths = { "organizer", "category", "venue" })
    @Query("""
            SELECT e
            FROM Event e
            JOIN e.venue v
            WHERE e.status = com.ticketing.event.model.EventStatus.PUBLISHED
                AND e.startDate > :now
                AND (
                    :query IS NULL
                    OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                    OR LOWER(COALESCE(e.description, '')) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                )
                AND (:categoryId IS NULL OR e.category.id = :categoryId)
                AND (:city IS NULL OR LOWER(v.city) = LOWER(CAST(:city AS string)))
            """)
    Page<Event> searchPublishedEvents(
            @Param("query") String query,
            @Param("categoryId") Long categoryId,
            @Param("city") String city,
            @Param("now") Instant now,
            Pageable pageable);
}
