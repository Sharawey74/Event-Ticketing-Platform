package com.ticketing.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.event.model.Venue;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
