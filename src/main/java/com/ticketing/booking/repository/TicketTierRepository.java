package com.ticketing.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.booking.model.TicketTier;

public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {
}
