package com.ticketing.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.ticketing.event.dto.CreateVenueRequest;
import com.ticketing.event.dto.VenueResponse;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.VenueRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {

    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private VenueService venueService;

    @Test
    @DisplayName("createVenue with valid data should persist and return")
    void createVenue_withValidData_shouldPersistAndReturn() {
        CreateVenueRequest request = CreateVenueRequest.builder()
                .name("Grand Arena")
                .address("100 Main St")
                .city("New York")
                .country("US")
                .totalCapacity(20_000)
                .build();

        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> {
            Venue venue = invocation.getArgument(0);
            venue.setId(10L);
            return venue;
        });

        VenueResponse response = venueService.createVenue(request);

        assertEquals(10L, response.getId());
        assertEquals("Grand Arena", response.getName());
        assertEquals("New York", response.getCity());
        assertEquals(20_000, response.getTotalCapacity());
        verify(venueRepository).save(any(Venue.class));
    }

    @Test
    @DisplayName("getVenue with invalid id should throw not found exception")
    void getVenue_withInvalidId_shouldThrowNotFoundException() {
        when(venueRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> venueService.getVenueById(404L));
    }

    @Test
    @DisplayName("listVenues should return paginated result")
    void listVenues_shouldReturnPaginatedResult() {
        Pageable pageable = PageRequest.of(0, 2);
        Venue venueOne = Venue.builder()
                .id(1L)
                .name("Grand Arena")
                .address("100 Main St")
                .city("New York")
                .country("US")
                .totalCapacity(20_000)
                .build();
        Venue venueTwo = Venue.builder()
                .id(2L)
                .name("Sunset Hall")
                .address("250 Ocean Ave")
                .city("Los Angeles")
                .country("US")
                .totalCapacity(8_500)
                .build();
        when(venueRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(venueOne, venueTwo)));

        Page<VenueResponse> response = venueService.listVenues(pageable);

        assertEquals(2, response.getTotalElements());
        assertEquals("Grand Arena", response.getContent().getFirst().getName());
        assertEquals("Sunset Hall", response.getContent().get(1).getName());
    }

    @Test
    @DisplayName("updateVenue with valid data should persist changes")
    void updateVenue_withValidData_shouldPersistChanges() {
        com.ticketing.event.dto.UpdateVenueRequest request = com.ticketing.event.dto.UpdateVenueRequest.builder()
                .name("Updated Arena")
                .address("999 Updated St")
                .city("Boston")
                .country("US")
                .capacity(30_000)
                .build();

        Venue existingVenue = Venue.builder()
                .id(10L)
                .name("Grand Arena")
                .address("100 Main St")
                .city("New York")
                .country("US")
                .totalCapacity(20_000)
                .build();

        when(venueRepository.findById(10L)).thenReturn(Optional.of(existingVenue));
        when(venueRepository.save(existingVenue)).thenReturn(existingVenue);

        VenueResponse response = venueService.updateVenue(10L, request);

        assertEquals(10L, response.getId());
        assertEquals("Updated Arena", response.getName());
        assertEquals("999 Updated St", response.getAddress());
        assertEquals("Boston", response.getCity());
        assertEquals(30_000, response.getTotalCapacity());
        verify(venueRepository).findById(10L);
        verify(venueRepository).save(existingVenue);
    }

    @Test
    @DisplayName("updateVenue with invalid id should throw not found exception")
    void updateVenue_withInvalidId_shouldThrowNotFoundException() {
        com.ticketing.event.dto.UpdateVenueRequest request = com.ticketing.event.dto.UpdateVenueRequest.builder()
                .name("Updated Arena")
                .address("999 Updated St")
                .city("Boston")
                .country("US")
                .capacity(30_000)
                .build();

        when(venueRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> venueService.updateVenue(404L, request));
        verify(venueRepository).findById(404L);
        verify(venueRepository, never()).save(any(Venue.class));
    }

    @Test
    @DisplayName("deleteVenue with valid id should remove venue")
    void deleteVenue_withValidId_shouldRemoveVenue() {
        Venue existingVenue = Venue.builder()
                .id(10L)
                .name("Grand Arena")
                .address("100 Main St")
                .city("New York")
                .country("US")
                .totalCapacity(20_000)
                .build();

        when(venueRepository.findById(10L)).thenReturn(Optional.of(existingVenue));

        venueService.deleteVenue(10L);

        verify(venueRepository).findById(10L);
        verify(venueRepository).deleteById(10L);
    }

    @Test
    @DisplayName("deleteVenue with invalid id should throw not found exception")
    void deleteVenue_withInvalidId_shouldThrowNotFoundException() {
        when(venueRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> venueService.deleteVenue(404L));
        verify(venueRepository).findById(404L);
        verify(venueRepository, never()).deleteById(eq(404L));
    }
}
