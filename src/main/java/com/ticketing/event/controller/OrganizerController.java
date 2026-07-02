package com.ticketing.event.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.event.dto.AttendeeResponse;
import com.ticketing.event.dto.OrganizerEventResponse;
import com.ticketing.event.service.OrganizerEventService;
import com.ticketing.user.service.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/organizer")
@RequiredArgsConstructor
@Tag(name = "Organizer")
public class OrganizerController {

    private static final Logger logger = LoggerFactory.getLogger(OrganizerController.class);

    private final OrganizerEventService organizerEventService;

    @Operation(summary = "Get my events", description = "ORGANIZER only. Returns all events owned by the authenticated organizer.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of events")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ORGANIZER")
    @GetMapping("/events")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<List<OrganizerEventResponse>>> getMyEvents(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long organizerId = userDetails.getId();
        List<OrganizerEventResponse> events = organizerEventService.getOrganizerEvents(organizerId);
        logger.info("Organizer {} fetched {} events", organizerId, events.size());
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @Operation(summary = "Get event attendees", description = "ORGANIZER only. Returns confirmed attendees for an event owned by the authenticated organizer.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of attendees")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not the event's organizer")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event not found")
    @GetMapping("/events/{eventId}/attendees")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<List<AttendeeResponse>>> getEventAttendees(
            @PathVariable Long eventId, Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long organizerId = userDetails.getId();
        List<AttendeeResponse> attendees = organizerEventService.getEventAttendees(eventId, organizerId);
        logger.info("Organizer {} fetched {} attendees for event {}", organizerId, attendees.size(), eventId);
        return ResponseEntity.ok(ApiResponse.success(attendees));
    }
}
