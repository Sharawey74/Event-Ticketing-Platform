package com.ticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.TicketTierRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class TicketTierServiceTest {

    @Mock
    private TicketTierRepository ticketTierRepository;

    @InjectMocks
    private TicketTierService ticketTierService;

    @Test
    @DisplayName("getAvailableCount with existing tier should return available count")
    void getAvailableCount_withExistingTier_shouldReturnAvailableCount() {
        TicketTier tier = TicketTier.builder()
                .id(1L)
                .availableCount(42)
                .build();
        when(ticketTierRepository.findById(1L)).thenReturn(Optional.of(tier));

        int availableCount = ticketTierService.getAvailableCount(1L);

        assertEquals(42, availableCount);
    }

    @Test
    @DisplayName("getAvailableCount with invalid tier should throw not found")
    void getAvailableCount_withInvalidTier_shouldThrowNotFound() {
        when(ticketTierRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> ticketTierService.getAvailableCount(404L));
    }

    @Test
    @DisplayName("getAvailableCount with zero inventory should return zero")
    void getAvailableCount_withZeroInventory_shouldReturnZero() {
        TicketTier tier = TicketTier.builder()
                .id(2L)
                .availableCount(0)
                .build();
        when(ticketTierRepository.findById(2L)).thenReturn(Optional.of(tier));

        int availableCount = ticketTierService.getAvailableCount(2L);

        assertEquals(0, availableCount);
    }
}
