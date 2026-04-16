package com.ticketing.event.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.dto.CreateEventRequest;
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
        logger.info("Event {} created by organizer {}", saved.getId(), organizerId);
        return toResponse(saved);
    }

    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findByIdWithDetails(id)
            .orElseThrow(() -> new EntityNotFoundException("Event not found: " + id));

        logger.info("Event {} fetched", id);
        return toResponse(event);
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
