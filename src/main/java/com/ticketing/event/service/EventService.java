package com.ticketing.event.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.common.config.RedisConfig;
import com.ticketing.inventory.service.InventoryService;

import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.CreateTicketTierRequest;
import com.ticketing.event.dto.EventFilterRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.model.Category;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.user.model.User;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventService {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final TicketTierRepository ticketTierRepository;
    private final InventoryService inventoryService;

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, Long organizerId) {
        validateEventDates(request.getStartDate(), request.getEndDate());

        Event event = Event.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .organizer(User.builder().id(organizerId).build())
            .category(Category.builder().id(request.getCategoryId()).build())
            .venue(Venue.builder().id(request.getVenueId()).build())
            .coverImageUrl(request.getCoverImageUrl())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .salesOpenDate(request.getSalesOpenDate())
            .salesCloseDate(request.getSalesCloseDate())
            .status(EventStatus.DRAFT)
            .dynamicPricingEnabled(Boolean.FALSE)
            .waitlistEnabled(Boolean.FALSE)
            .build();

        Event saved = eventRepository.save(event);

        if (request.getTicketTiers() != null) {
            for (CreateTicketTierRequest tierReq : request.getTicketTiers()) {
                TicketTier tier = TicketTier.builder()
                    .event(saved)
                    .tierName(tierReq.getTierName())
                    .description(tierReq.getDescription())
                    .basePrice(tierReq.getBasePrice())
                    .totalCapacity(tierReq.getTotalCapacity())
                    .availableCount(tierReq.getTotalCapacity())
                    .maxPerBooking(tierReq.getMaxPerBooking() != null
                        ? tierReq.getMaxPerBooking()
                        : com.ticketing.common.util.BusinessConstants.MAX_TICKETS_PER_BOOKING)
                    .build();
                TicketTier savedTier = ticketTierRepository.save(tier);
                inventoryService.setAvailableCount(savedTier.getId(), savedTier.getTotalCapacity());
            }
        }

        logger.info("Event {} created by organizer {}", saved.getId(), organizerId);
        return toResponse(saved);
    }

    @Cacheable(value = RedisConfig.EVENT_CACHE, key = "#id")
    public com.ticketing.event.dto.EventDetailResponse getEventById(Long id) {
        Event event = eventRepository.findByIdWithDetails(id)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + id));

        logger.info("Event {} fetched", id);
        return toDetailResponse(event);
    }

    public Page<EventResponse> getEvents(EventFilterRequest filter, Pageable pageable) {
        EventStatus status = filter.getStatus() == null ? EventStatus.PUBLISHED : filter.getStatus();
        return eventRepository.findByStatusAndCategoryIdAndVenueCity(
                status,
                filter.getCategoryId(),
                filter.getCity(),
                pageable)
            .map(this::toResponse);
    }

    @Transactional
    @CacheEvict(value = {RedisConfig.EVENT_CACHE, RedisConfig.EVENT_LIST_CACHE}, key = "#eventId")
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request, Long organizerId) {
        Event event = eventRepository.findByIdWithDetails(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        validateOwnership(event, organizerId);

        if (request.getStartDate() != null || request.getEndDate() != null) {
            Instant startDate = request.getStartDate() == null ? event.getStartDate() : request.getStartDate();
            Instant endDate = request.getEndDate() == null ? event.getEndDate() : request.getEndDate();
            validateEventDates(startDate, endDate);
            event.setStartDate(startDate);
            event.setEndDate(endDate);
        }

        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getCategoryId() != null) {
            event.setCategory(Category.builder().id(request.getCategoryId()).build());
        }
        if (request.getVenueId() != null) {
            event.setVenue(Venue.builder().id(request.getVenueId()).build());
        }
        if (request.getCoverImageUrl() != null) {
            event.setCoverImageUrl(request.getCoverImageUrl());
        }
        if (request.getSalesOpenDate() != null) {
            event.setSalesOpenDate(request.getSalesOpenDate());
        }
        if (request.getSalesCloseDate() != null) {
            event.setSalesCloseDate(request.getSalesCloseDate());
        }
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
        }

        Event updated = eventRepository.save(event);
        logger.info("Event {} updated by organizer {}", eventId, organizerId);
        return toResponse(updated);
    }

    @Transactional
    public void deleteEvent(Long eventId, Long organizerId) {
        Event event = eventRepository.findByIdWithDetails(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        validateOwnership(event, organizerId);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ValidationException("Only draft events can be deleted");
        }

        eventRepository.delete(event);
        logger.info("Event {} deleted by organizer {}", eventId, organizerId);
    }

    @Transactional
    @CacheEvict(value = {RedisConfig.EVENT_CACHE, RedisConfig.EVENT_LIST_CACHE}, key = "#eventId", allEntries = true)
    public EventResponse publishEvent(Long eventId, Long organizerId) {
        Event event = eventRepository.findByIdWithDetails(eventId)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        validateOwnership(event, organizerId);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new ValidationException("Only draft events can be published");
        }

        validatePublishRequiredFields(event);
        event.setStatus(EventStatus.PUBLISHED);

        Event updated = eventRepository.save(event);
        logger.info("Event {} published by organizer {}", eventId, organizerId);
        return toResponse(updated);
    }

    private void validateOwnership(Event event, Long organizerId) {
        Long eventOrganizerId = event.getOrganizer() == null ? null : event.getOrganizer().getId();
        if (eventOrganizerId == null || !eventOrganizerId.equals(organizerId)) {
            throw new AccessDeniedException("Only the organizer can modify this event");
        }
    }

    private void validateEventDates(Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null) {
            throw new ValidationException("Event start and end dates are required");
        }
        if (!startDate.isAfter(Instant.now())) {
            throw new ValidationException("Event start date must be in the future");
        }
        if (!endDate.isAfter(startDate)) {
            throw new ValidationException("Event end date must be after start date");
        }
    }

    private void validatePublishRequiredFields(Event event) {
        if (event.getTitle() == null || event.getTitle().isBlank()) {
            throw new ValidationException("Event title is required before publishing");
        }
        if (event.getStartDate() == null || event.getEndDate() == null) {
            throw new ValidationException("Event dates are required before publishing");
        }
        if (event.getCategory() == null || event.getVenue() == null) {
            throw new ValidationException("Event category and venue are required before publishing");
        }
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.builder()
            .id(event.getId())
            .title(event.getTitle())
            .description(event.getDescription())
            .organizerId(event.getOrganizer() == null ? null : event.getOrganizer().getId())
            .categoryId(event.getCategory() == null ? null : event.getCategory().getId())
            .categoryName(event.getCategory() == null ? null : event.getCategory().getName())
            .venueId(event.getVenue() == null ? null : event.getVenue().getId())
            .startDate(event.getStartDate())
            .endDate(event.getEndDate())
            .salesOpenDate(event.getSalesOpenDate())
            .salesCloseDate(event.getSalesCloseDate())
            .coverImageUrl(event.getCoverImageUrl())
            .status(event.getStatus())
            .waitlistEnabled(event.getWaitlistEnabled())
            .minPrice(event.getMinPrice())
            .build();
    }

    private com.ticketing.event.dto.EventDetailResponse toDetailResponse(Event event) {
        return com.ticketing.event.dto.EventDetailResponse.builder()
            .id(event.getId())
            .title(event.getTitle())
            .description(event.getDescription())
            .startDate(event.getStartDate())
            .endDate(event.getEndDate())
            .salesOpenDate(event.getSalesOpenDate())
            .salesCloseDate(event.getSalesCloseDate())
            .coverImageUrl(event.getCoverImageUrl())
            .status(event.getStatus())
            .dynamicPricingEnabled(event.getDynamicPricingEnabled())
            .waitlistEnabled(event.getWaitlistEnabled())
            .organizer(event.getOrganizer() == null ? null : com.ticketing.event.dto.EventDetailResponse.OrganizerInfo.builder()
                .id(event.getOrganizer().getId())
                .name(event.getOrganizer().getFirstName() + " " + event.getOrganizer().getLastName())
                .email(event.getOrganizer().getEmail())
                .build())
            .category(event.getCategory() == null ? null : com.ticketing.event.dto.CategoryResponse.builder()
                .id(event.getCategory().getId())
                .name(event.getCategory().getName())
                .description(event.getCategory().getDescription())
                .build())
            .venue(event.getVenue() == null ? null : com.ticketing.event.dto.VenueResponse.builder()
                .id(event.getVenue().getId())
                .name(event.getVenue().getName())
                .address(event.getVenue().getAddress())
                .city(event.getVenue().getCity())
                .country(event.getVenue().getCountry())
                .totalCapacity(event.getVenue().getTotalCapacity())
                .build())
            .ticketTiers(event.getTicketTiers() == null ? java.util.List.of() : event.getTicketTiers().stream()
                .map(t -> com.ticketing.event.dto.TicketTierResponse.builder()
                    .id(t.getId())
                    .tierName(t.getTierName())
                    .description(t.getDescription())
                    .basePrice(t.getBasePrice())
                    .totalCapacity(t.getTotalCapacity())
                    .availableCount(t.getAvailableCount())
                    .maxPerBooking(t.getMaxPerBooking())
                    .build())
                .toList())
            .build();
    }
}
