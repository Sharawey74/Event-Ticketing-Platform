package com.ticketing.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.TicketTierRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketTierService {

    private static final Logger logger = LoggerFactory.getLogger(TicketTierService.class);

    private final TicketTierRepository ticketTierRepository;

    public int getAvailableCount(Long tierId) {
        TicketTier tier = ticketTierRepository.findById(tierId)
                .orElseThrow(() -> new EntityNotFoundException("Ticket tier not found: " + tierId));

        logger.info("Available count fetched for tier {}", tierId);
        return tier.getAvailableCount();
    }
}
