package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.config.StateMachineFactory;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingEvent;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.common.service.DistributedLockService;
import com.ticketing.common.util.BusinessConstants;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.inventory.service.InventoryService;
import com.ticketing.pricing.service.PricingEngine;
import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

/**
 * Unit tests for BookingService.reserveTickets() — covers validation, TOCTOU
 * guard,
 * lock acquisition failure, and happy path (Fix 8.1 verified).
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceReserveTest {

        @Mock
        private BookingRepository bookingRepository;
        @Mock
        private EventRepository eventRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private TicketTierRepository ticketTierRepository;
        @Mock
        private InventoryService inventoryService;
        @Mock
        private DistributedLockService lockService;
        @Mock
        private StateMachineFactory<BookingState, BookingEvent> stateMachineFactory;
        @Mock
        private PricingEngine pricingEngine;

        @InjectMocks
        private BookingService bookingService;

        private User user;
        private Event event;
        private TicketTier tier;

        @BeforeEach
        void setUp() {
                user = User.builder().id(1L).email("user@test.com").role(Role.USER).build();

                event = Event.builder()
                                .id(10L)
                                .title("Concert")
                                .status(EventStatus.PUBLISHED)
                                .startDate(Instant.now().plusSeconds(86400 * 30))
                                .build();

                tier = TicketTier.builder()
                                .id(100L)
                                .tierName("General")
                                .basePrice(BigDecimal.valueOf(50))
                                .totalCapacity(100)
                                .availableCount(10)
                                .event(event)
                                .build();
        }

        @Test
        @DisplayName("reserveTickets: invalid quantity zero should throw IllegalArgumentException")
        void reserveTickets_whenQuantityZero_shouldThrowIllegalArgumentException() {
                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 0))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Invalid ticket quantity");

                verify(eventRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("reserveTickets: quantity exceeding max should throw IllegalArgumentException")
        void reserveTickets_whenQuantityExceedsMax_shouldThrowIllegalArgumentException() {
                int tooMany = BusinessConstants.MAX_TICKETS_PER_BOOKING + 1;

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, tooMany))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Max allowed");

                verify(eventRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("reserveTickets: event not found should throw EntityNotFoundException")
        void reserveTickets_whenEventNotFound_shouldThrowEntityNotFoundException() {
                when(eventRepository.findById(10L)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 1))
                                .isInstanceOf(EntityNotFoundException.class)
                                .hasMessageContaining("10");
        }

        @Test
        @DisplayName("reserveTickets: unpublished event should throw IllegalStateException")
        void reserveTickets_whenEventNotPublished_shouldThrowIllegalStateException() {
                event = Event.builder().id(10L).status(EventStatus.DRAFT).build();
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 1))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("not open for booking");
        }

        @Test
        @DisplayName("reserveTickets: tier not found should throw EntityNotFoundException")
        void reserveTickets_whenTierNotFound_shouldThrowEntityNotFoundException() {
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
                when(ticketTierRepository.findById(100L)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 1))
                                .isInstanceOf(EntityNotFoundException.class)
                                .hasMessageContaining("100");
        }

        @Test
        @DisplayName("reserveTickets: tier not belonging to event should throw IllegalArgumentException")
        void reserveTickets_whenTierBelongsToDifferentEvent_shouldThrowIllegalArgumentException() {
                Event otherEvent = Event.builder().id(99L).status(EventStatus.PUBLISHED).build();
                TicketTier wrongTier = TicketTier.builder()
                                .id(100L)
                                .event(otherEvent)
                                .totalCapacity(50)
                                .availableCount(10)
                                .build();
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
                when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(wrongTier));

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 1))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("does not belong");
        }

        @Test
        @DisplayName("reserveTickets: insufficient inventory before lock should throw IllegalStateException")
        void reserveTickets_whenInventoryInsufficient_shouldThrowIllegalStateException() {
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
                when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));
                when(inventoryService.getAvailableCount(100L)).thenReturn(0);

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 2))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Not enough tickets");

                verify(lockService, never()).acquireLock(any(), any(), anyLong());
        }

        @Test
        @DisplayName("reserveTickets: lock acquisition failure should throw IllegalStateException")
        void reserveTickets_whenLockNotAcquired_shouldThrowIllegalStateException() {
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
                when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));
                when(inventoryService.getAvailableCount(100L)).thenReturn(5);
                when(lockService.acquireLock(any(), any(), anyLong())).thenReturn(false);

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 1))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Could not acquire lock");

                verify(inventoryService, never()).reserveSeat(anyLong(), anyInt());
        }

        @Test
        @DisplayName("reserveTickets: TOCTOU guard fires when inventory depletes after lock acquired")
        void reserveTickets_whenInventoryDepletedInsideLock_shouldThrowIllegalStateException() {
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
                when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));
                // first check: enough; second check (inside lock): depleted
                when(inventoryService.getAvailableCount(100L)).thenReturn(5).thenReturn(0);
                when(lockService.acquireLock(any(), any(), anyLong())).thenReturn(true);

                assertThatThrownBy(() -> bookingService.reserveTickets(1L, 10L, 100L, 1))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Not enough tickets");

                verify(inventoryService, never()).reserveSeat(anyLong(), anyInt());
                verify(lockService).releaseLock(any(), any());
        }

        @Test
        @DisplayName("reserveTickets: happy path creates and persists booking with correct amount")
        void reserveTickets_whenAllConditionsMet_shouldCreateAndSaveBooking() {
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
                when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));
                when(inventoryService.getAvailableCount(100L)).thenReturn(5);
                when(lockService.acquireLock(any(), any(), anyLong())).thenReturn(true);
                when(inventoryService.reserveSeat(100L, 2)).thenReturn(true);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(pricingEngine.calculateFinalPrice(any(), any(), eq(2), anyInt(), anyInt()))
                                .thenReturn(BigDecimal.valueOf(50));
                when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

                Booking booking = bookingService.reserveTickets(1L, 10L, 100L, 2);

                assertThat(booking.getState()).isEqualTo(BookingState.RESERVED);
                assertThat(booking.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
                assertThat(booking.getTickets()).hasSize(2);
                assertThat(booking.getUser()).isEqualTo(user);
                verify(lockService).releaseLock(any(), any());
        }

        @Test
        @DisplayName("reserveTickets: a successful reservation decrements the tier's availableCount in the database (Fix D19-1)")
        void reserveTickets_whenSuccessful_shouldDecrementTierAvailableCountInDatabase() {
                when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
                when(ticketTierRepository.findById(100L)).thenReturn(Optional.of(tier));
                when(inventoryService.getAvailableCount(100L)).thenReturn(5);
                when(lockService.acquireLock(any(), any(), anyLong())).thenReturn(true);
                when(inventoryService.reserveSeat(100L, 3)).thenReturn(true);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(pricingEngine.calculateFinalPrice(any(), any(), eq(3), anyInt(), anyInt()))
                                .thenReturn(BigDecimal.valueOf(50));
                when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

                bookingService.reserveTickets(1L, 10L, 100L, 3);

                verify(ticketTierRepository).save(tier);
                assertThat(tier.getAvailableCount()).isEqualTo(7);
        }
}
