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
import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventFilterRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.service.EventService;
import com.ticketing.user.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/events")
@Validated
@RequiredArgsConstructor
public class EventController {

    private static final Logger logger = LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
        @Valid @RequestBody CreateEventRequest request,
        Authentication authentication) {

        Long organizerId = authService.getUserIdByEmail(authentication.getName());
        EventResponse response = eventService.createEvent(request, organizerId);
        logger.info("Create event endpoint finished for organizer {}", organizerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable Long id) {
        EventResponse response = eventService.getEventById(id);
        logger.info("Get event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventResponse>>> getEvents(
        @RequestParam(required = false) EventStatus status,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String city,
        Pageable pageable) {

        EventFilterRequest filter = EventFilterRequest.builder()
            .status(status)
            .categoryId(categoryId)
            .city(city)
            .build();

        Page<EventResponse> response = eventService.getEvents(filter, pageable);
        logger.info("Get events endpoint finished with status {} category {} city {}", status, categoryId, city);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
        @PathVariable Long id,
        @Valid @RequestBody UpdateEventRequest request,
        Authentication authentication) {

        Long organizerId = authService.getUserIdByEmail(authentication.getName());
        EventResponse response = eventService.updateEvent(id, request, organizerId);
        logger.info("Update event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable Long id, Authentication authentication) {
        Long organizerId = authService.getUserIdByEmail(authentication.getName());
        eventService.deleteEvent(id, organizerId);
        logger.info("Delete event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> publishEvent(@PathVariable Long id, Authentication authentication) {
        Long organizerId = authService.getUserIdByEmail(authentication.getName());
        EventResponse response = eventService.publishEvent(id, organizerId);
        logger.info("Publish event endpoint finished for event {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
