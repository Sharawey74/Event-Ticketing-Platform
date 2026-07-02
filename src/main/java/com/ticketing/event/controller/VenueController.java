package com.ticketing.event.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.event.dto.CreateVenueRequest;
import com.ticketing.event.dto.VenueResponse;
import com.ticketing.event.service.VenueService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/venues")
@Validated
@RequiredArgsConstructor
@Tag(name = "Venues")
public class VenueController {

    private static final Logger logger = LoggerFactory.getLogger(VenueController.class);

    private final VenueService venueService;

    @Operation(summary = "Create a venue", description = "ADMIN only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Venue created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VenueResponse>> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        VenueResponse response = venueService.createVenue(request);
        logger.info("Create venue endpoint finished for venue {}", response.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get venue details", description = "Publicly viewable.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Venue found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Venue not found")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VenueResponse>> getVenue(@PathVariable Long id) {
        VenueResponse response = venueService.getVenueById(id);
        logger.info("Get venue endpoint finished for venue {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "List venues", description = "Publicly viewable paginated venue list.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Page of venues")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<VenueResponse>>> listVenues(Pageable pageable) {
        Page<VenueResponse> response = venueService.listVenues(pageable);
        logger.info("List venues endpoint finished with page {}", pageable.getPageNumber());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Update a venue", description = "ADMIN only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Venue updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Venue not found")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VenueResponse>> updateVenue(
            @PathVariable Long id,
            @Valid @RequestBody com.ticketing.event.dto.UpdateVenueRequest request) {
        VenueResponse response = venueService.updateVenue(id, request);
        logger.info("Update venue endpoint finished for venue {}", id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Delete a venue", description = "ADMIN only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Venue deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ADMIN")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Venue not found")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteVenue(@PathVariable Long id) {
        venueService.deleteVenue(id);
        logger.info("Delete venue endpoint finished for venue {}", id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
