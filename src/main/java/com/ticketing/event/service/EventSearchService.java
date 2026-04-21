package com.ticketing.event.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.model.Event;
import com.ticketing.event.repository.EventRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventSearchService {

    private static final Logger logger = LoggerFactory.getLogger(EventSearchService.class);

    private final EventRepository eventRepository;

    public Page<EventResponse> searchEvents(String query, Long categoryId, String city, Pageable pageable) {
        String normalizedQuery = normalize(query);
        String normalizedCity = normalize(city);

        Page<EventResponse> results = eventRepository.searchPublishedEvents(
                normalizedQuery,
                categoryId,
                normalizedCity,
                Instant.now(),
                pageable)
                .map(this::toResponse);

        logger.info("Search completed with query {} category {} city {} count {}",
                normalizedQuery,
                categoryId,
                normalizedCity,
                results.getTotalElements());
        return results;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .organizerId(event.getOrganizer() == null ? null : event.getOrganizer().getId())
                .categoryId(event.getCategory() == null ? null : event.getCategory().getId())
                .venueId(event.getVenue() == null ? null : event.getVenue().getId())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .salesOpenDate(event.getSalesOpenDate())
                .salesCloseDate(event.getSalesCloseDate())
                .coverImageUrl(event.getCoverImageUrl())
                .status(event.getStatus())
                .dynamicPricingEnabled(event.getDynamicPricingEnabled())
                .waitlistEnabled(event.getWaitlistEnabled())
                .build();
    }
}
