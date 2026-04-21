package com.ticketing.event.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.dto.CreateVenueRequest;
import com.ticketing.event.dto.VenueResponse;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.VenueRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VenueService {

    private static final Logger logger = LoggerFactory.getLogger(VenueService.class);

    private final VenueRepository venueRepository;

    @Transactional
    public VenueResponse createVenue(CreateVenueRequest request) {
        Venue venue = Venue.builder()
                .name(request.getName())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry())
                .totalCapacity(request.getTotalCapacity())
                .build();

        Venue saved = venueRepository.save(venue);
        logger.info("Venue {} created for city {}", saved.getId(), saved.getCity());
        return toResponse(saved);
    }

    public VenueResponse getVenueById(Long venueId) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new EntityNotFoundException("Venue not found: " + venueId));

        logger.info("Venue {} fetched", venueId);
        return toResponse(venue);
    }

    public Page<VenueResponse> listVenues(Pageable pageable) {
        return venueRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public VenueResponse updateVenue(Long venueId, CreateVenueRequest request) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new EntityNotFoundException("Venue not found: " + venueId));

        venue.setName(request.getName());
        venue.setAddress(request.getAddress());
        venue.setCity(request.getCity());
        venue.setCountry(request.getCountry());
        venue.setTotalCapacity(request.getTotalCapacity());

        Venue updated = venueRepository.save(venue);
        logger.info("Venue {} updated", venueId);
        return toResponse(updated);
    }

    @Transactional
    public void deleteVenue(Long venueId) {
        venueRepository.findById(venueId)
                .orElseThrow(() -> new EntityNotFoundException("Venue not found: " + venueId));

        venueRepository.deleteById(venueId);
        logger.info("Venue {} deleted", venueId);
    }

    private VenueResponse toResponse(Venue venue) {
        return VenueResponse.builder()
                .id(venue.getId())
                .name(venue.getName())
                .address(venue.getAddress())
                .city(venue.getCity())
                .country(venue.getCountry())
                .totalCapacity(venue.getTotalCapacity())
                .build();
    }
}
