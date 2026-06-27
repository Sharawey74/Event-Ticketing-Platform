package com.ticketing.booking.service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.dto.BookingDetailsResponse;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.repository.BookingRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-side service for booking queries.
 *
 * Enforces the core invariant: no payment confirmation = no ticket/QR access.
 * Tickets and QR codes are only included in responses for CONFIRMED or ATTENDED bookings.
 *
 * CC-1: All log statements include MDC correlationId.
 * CC-2: No magic numbers — BookingState enum used directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BookingQueryService {

    private final BookingRepository bookingRepository;

    public List<BookingResponse> getMyBookings(Long userId) {
        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Fetching bookings for user {}", correlationId, userId);

        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toBookingResponse)
                .collect(Collectors.toList());
    }

    public BookingDetailsResponse getBookingById(Long bookingId, Long requestingUserId) {
        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Fetching booking {} for user {}", correlationId, bookingId, requestingUserId);

        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(requestingUserId)) {
            log.warn("[{}] [booking] Access denied: booking {} requested by user {} but owned by {}",
                    correlationId, bookingId, requestingUserId, booking.getUser().getId());
            throw new AccessDeniedException("Booking does not belong to the requesting user");
        }

        boolean ticketsVisible = booking.getState() == BookingState.CONFIRMED
                || booking.getState() == BookingState.ATTENDED;

        List<BookingDetailsResponse.TicketDto> tickets = ticketsVisible
                ? toTicketDtos(booking)
                : List.of();

        return BookingDetailsResponse.builder()
                .id(booking.getId())
                .reference("EVT-" + booking.getId())
                .state(booking.getState().name())
                .expiresAt(booking.getExpiresAt())
                .totalPrice(booking.getTotalAmount())
                .event(BookingDetailsResponse.EventDto.builder()
                        .title(booking.getEvent().getTitle())
                        .startDate(booking.getEvent().getStartDate())
                        .venueName(booking.getEvent().getVenue() != null
                                ? booking.getEvent().getVenue().getName() : "")
                        .coverImageUrl(booking.getEvent().getCoverImageUrl())
                        .build())
                .tickets(tickets)
                .build();
    }

    private List<BookingDetailsResponse.TicketDto> toTicketDtos(Booking booking) {
        return booking.getTickets().stream()
                .map(ticket -> BookingDetailsResponse.TicketDto.builder()
                        .id(ticket.getId())
                        .qrCode(ticket.getQrCode())
                        .tierName(ticket.getTier().getTierName())
                        .gate("Main Gate")
                        .seat(ticket.getSeatNumber())
                        .code("TCK-" + ticket.getId())
                        .build())
                .collect(Collectors.toList());
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .bookingId(booking.getId())
                .state(booking.getState().name())
                .eventTitle(booking.getEvent().getTitle())
                .eventDate(booking.getEvent().getStartDate())
                .venueName(booking.getEvent().getVenue() != null
                        ? booking.getEvent().getVenue().getName() : null)
                .totalAmount(booking.getTotalAmount())
                .expiresAt(booking.getExpiresAt())
                .coverImageUrl(booking.getEvent().getCoverImageUrl())
                .categoryName(booking.getEvent().getCategory() != null
                        ? booking.getEvent().getCategory().getName() : null)
                .quantity(booking.getTickets().size())
                .build();
    }
}
