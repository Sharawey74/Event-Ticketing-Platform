package com.ticketing.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.ticketing.booking.dto.BookingDetailsResponse;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.Ticket;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.event.model.Event;
import com.ticketing.user.model.User;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class BookingQueryServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private BookingQueryService bookingQueryService;

    // ── getBookingById ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getBookingById when booking is RESERVED should return empty ticket list")
    void getBookingById_whenBookingIsReserved_shouldReturnEmptyTicketList() {
        Booking booking = reservedBookingOwnedBy(1L);

        when(bookingRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(booking));

        BookingDetailsResponse response = bookingQueryService.getBookingById(1L, 1L);

        assertNotNull(response);
        assertTrue(response.getTickets().isEmpty(),
                "RESERVED booking must not expose tickets or QR codes");
    }

    @Test
    @DisplayName("getBookingById when booking is PAYMENT_PENDING should return empty ticket list")
    void getBookingById_whenBookingIsPaymentPending_shouldReturnEmptyTicketList() {
        Booking booking = bookingOwnedBy(1L, BookingState.PAYMENT_PENDING);

        when(bookingRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(booking));

        BookingDetailsResponse response = bookingQueryService.getBookingById(1L, 1L);

        assertTrue(response.getTickets().isEmpty(),
                "PAYMENT_PENDING booking must not expose tickets or QR codes");
    }

    @Test
    @DisplayName("getBookingById when booking is CONFIRMED should return tickets with QR code")
    void getBookingById_whenBookingIsConfirmed_shouldReturnTicketsWithQr() {
        TicketTier tier = TicketTier.builder().id(5L).tierName("VIP").build();
        Ticket ticket = Ticket.builder()
                .id(100L)
                .qrCode("qr-secret-abc123")
                .tier(tier)
                .checkInStatus(false)
                .build();
        Booking booking = confirmedBookingOwnedBy(1L, List.of(ticket));

        when(bookingRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(booking));

        BookingDetailsResponse response = bookingQueryService.getBookingById(1L, 1L);

        assertFalse(response.getTickets().isEmpty(), "CONFIRMED booking must return tickets");
        assertEquals("qr-secret-abc123", response.getTickets().get(0).getQrCode(),
                "QR code must be present for CONFIRMED booking");
    }

    @Test
    @DisplayName("getBookingById when booking is ATTENDED should return tickets with QR code")
    void getBookingById_whenBookingIsAttended_shouldReturnTicketsWithQr() {
        TicketTier tier = TicketTier.builder().id(5L).tierName("General").build();
        Ticket ticket = Ticket.builder()
                .id(101L)
                .qrCode("qr-attended-xyz")
                .tier(tier)
                .checkInStatus(true)
                .build();
        Booking booking = bookingWithTicketsOwnedBy(1L, BookingState.ATTENDED, List.of(ticket));

        when(bookingRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(booking));

        BookingDetailsResponse response = bookingQueryService.getBookingById(1L, 1L);

        assertFalse(response.getTickets().isEmpty(), "ATTENDED booking must return tickets");
        assertEquals("qr-attended-xyz", response.getTickets().get(0).getQrCode());
    }

    @Test
    @DisplayName("getBookingById when EXPIRED should return empty ticket list")
    void getBookingById_whenBookingIsExpired_shouldReturnEmptyTicketList() {
        Booking booking = bookingOwnedBy(1L, BookingState.EXPIRED);

        when(bookingRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(booking));

        BookingDetailsResponse response = bookingQueryService.getBookingById(1L, 1L);

        assertTrue(response.getTickets().isEmpty(),
                "EXPIRED booking must not expose tickets");
    }

    @Test
    @DisplayName("getBookingById when requested by different user should throw AccessDeniedException")
    void getBookingById_whenRequestedByDifferentUser_shouldThrowAccessDenied() {
        Booking booking = reservedBookingOwnedBy(1L);

        when(bookingRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class,
                () -> bookingQueryService.getBookingById(1L, 99L),
                "Must reject access when userId does not match booking owner");
    }

    @Test
    @DisplayName("getBookingById when booking not found should throw EntityNotFoundException")
    void getBookingById_whenBookingNotFound_shouldThrowEntityNotFoundException() {
        when(bookingRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> bookingQueryService.getBookingById(999L, 1L));
    }

    // ── getMyBookings ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyBookings when user has bookings should return mapped list")
    void getMyBookings_whenUserHasBookings_shouldReturnMappedList() {
        Booking booking = reservedBookingOwnedBy(1L);

        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(booking));

        List<BookingResponse> responses = bookingQueryService.getMyBookings(1L);

        assertEquals(1, responses.size());
        assertEquals("RESERVED", responses.get(0).getState());
        assertEquals("Test Event", responses.get(0).getEventTitle());
    }

    @Test
    @DisplayName("getMyBookings when user has no bookings should return empty list")
    void getMyBookings_whenUserHasNoBookings_shouldReturnEmptyList() {
        when(bookingRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        List<BookingResponse> responses = bookingQueryService.getMyBookings(1L);

        assertTrue(responses.isEmpty());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Booking reservedBookingOwnedBy(Long userId) {
        return bookingOwnedBy(userId, BookingState.RESERVED);
    }

    private Booking bookingOwnedBy(Long userId, BookingState state) {
        User owner = User.builder().id(userId).build();
        Event event = Event.builder()
                .id(10L)
                .title("Test Event")
                .startDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        return Booking.builder()
                .id(1L)
                .user(owner)
                .event(event)
                .state(state)
                .totalAmount(BigDecimal.valueOf(500))
                .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .tickets(new ArrayList<>())
                .build();
    }

    private Booking confirmedBookingOwnedBy(Long userId, List<Ticket> tickets) {
        return bookingWithTicketsOwnedBy(userId, BookingState.CONFIRMED, tickets);
    }

    private Booking bookingWithTicketsOwnedBy(Long userId, BookingState state, List<Ticket> tickets) {
        User owner = User.builder().id(userId).build();
        Event event = Event.builder()
                .id(10L)
                .title("Test Event")
                .startDate(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        return Booking.builder()
                .id(1L)
                .user(owner)
                .event(event)
                .state(state)
                .totalAmount(BigDecimal.valueOf(500))
                .tickets(new ArrayList<>(tickets))
                .build();
    }
}
