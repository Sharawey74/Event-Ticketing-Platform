package com.ticketing.event.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.common.dto.PageResponse;
import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventFilterRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.service.EventService;
import com.ticketing.user.service.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@Validated
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private static final Logger logger = LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;

    @Operation(summary = "Create an event", description = "Creates a new event in DRAFT status. Must be explicitly published via /publish before bookings are accepted.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Event created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ORGANIZER")
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
        @Valid @RequestBody CreateEventRequest request,
        Authentication authentication) {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long organizerId = userDetails.getId();
        EventResponse response = eventService.createEvent(request, organizerId);
        logger.info("Create event endpoint finished for organizer {}", organizerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get event details", description = "Publicly viewable event detail including venue, category, and ticket tiers.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Event found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event not found")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<com.ticketing.event.dto.EventDetailResponse>> getEvent(@PathVariable Long id) {
        com.ticketing.event.dto.EventDetailResponse response = eventService.getEventById(id);
        logger.info("Get event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "List events", description = "Publicly viewable paginated event list with optional status/category/city filters.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of events")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> getEvents(
        @RequestParam(required = false) EventStatus status,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String city,
        Pageable pageable) {

        EventFilterRequest filter = EventFilterRequest.builder()
            .status(status)
            .categoryId(categoryId)
            .city(city)
            .build();

        Page<EventResponse> page = eventService.getEvents(filter, pageable);
        PageResponse<EventResponse> response = PageResponse.of(page);
        logger.info("Get events endpoint finished with status {} category {} city {}", status, categoryId, city);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update an event", description = "Updates an event owned by the authenticated organizer.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Event updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not the event's organizer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event not found")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
        @PathVariable Long id,
        @Valid @RequestBody UpdateEventRequest request,
        Authentication authentication) {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long organizerId = userDetails.getId();
        EventResponse response = eventService.updateEvent(id, request, organizerId);
        logger.info("Update event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Delete an event", description = "Deletes an event owned by the authenticated organizer.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Event deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not the event's organizer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event not found")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id, Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long organizerId = userDetails.getId();
        eventService.deleteEvent(id, organizerId);
        logger.info("Delete event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Publish an event", description = "Transitions an event from DRAFT to PUBLISHED. Bookings are only accepted for PUBLISHED events.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Event published")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not the event's organizer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event not found")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> publishEvent(@PathVariable Long id, Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long organizerId = userDetails.getId();
        EventResponse response = eventService.publishEvent(id, organizerId);
        logger.info("Publish event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
