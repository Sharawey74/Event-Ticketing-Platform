package com.ticketing.booking.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.service.BookingService;
import com.ticketing.common.dto.ApiResponse;
import com.ticketing.payment.dto.RefundResponse;
import com.ticketing.payment.service.RefundService;
import com.ticketing.user.service.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Booking operations.
 * Thin controller — all business logic lives in BookingService / RefundService.
 *
 * Endpoints:
 *   POST /api/v1/bookings/{bookingId}/check-in  — ORGANIZER/ADMIN only
 *   POST /api/v1/bookings/{id}/refunds          — Any authenticated user (ownership enforced in service)
 *   POST /api/v1/bookings                       — Authenticated user creates a booking
 *   GET  /api/v1/bookings/my                    — Authenticated user fetches their bookings
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    private final RefundService  refundService;
    private final BookingRepository bookingRepository;

    /**
     * Check-in a booking (scan QR at venue).
     * Fix 8.2: Two guards — @PreAuthorize for role (HTTP layer) + CheckInGuard in state machine (domain layer).
     */
    @PostMapping("/{bookingId}/check-in")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<Void> checkIn(@PathVariable Long bookingId) {
        bookingService.checkIn(bookingId);
        return ResponseEntity.ok().build();
    }

    /**
     * Request a refund for a confirmed booking.
     *
     * Three-tier refund window:
     *   ≥ 7 days before event  → full refund
     *   3–6 days before event  → 50% partial refund
     *   < 3 days before event  → denied (reason stored on booking — Fix 12.1)
     *
     * The Idempotency-Key header is accepted per API protocol but idempotency
     * enforcement is deferred to Day 20 (rate-limiting + denylist pass).
     *
     * Ownership is validated inside RefundService (booking.user.id == authenticated user id).
     */
    @PostMapping("/{id}/refunds")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Refund requested for booking {} by user {}",
                correlationId, id, userDetails.getId());

        RefundResponse response = refundService.requestRefund(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Booking booking = bookingService.reserveTickets(
                userDetails.getId(), 
                request.getEventId(), 
                request.getTierId(), 
                request.getQuantity()
        );
        
        BookingResponse response = BookingResponse.builder()
                .id(booking.getId())
                .bookingId(booking.getId())
                .state(booking.getState().name())
                .eventTitle(booking.getEvent().getTitle())
                .eventDate(booking.getEvent().getStartDate())
                .venueName(booking.getEvent().getVenue() != null ? booking.getEvent().getVenue().getName() : null)
                .totalAmount(booking.getTotalAmount())
                .expiresAt(booking.getExpiresAt())
                .coverImageUrl(booking.getEvent().getCoverImageUrl())
                .categoryName(booking.getEvent().getCategory() != null ? booking.getEvent().getCategory().getName() : null)
                .quantity(request.getQuantity())
                .build();
                
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(userDetails.getId());
        
        List<BookingResponse> response = bookings.stream().map(booking -> BookingResponse.builder()
                .id(booking.getId())
                .bookingId(booking.getId())
                .state(booking.getState().name())
                .eventTitle(booking.getEvent().getTitle())
                .eventDate(booking.getEvent().getStartDate())
                .venueName(booking.getEvent().getVenue() != null ? booking.getEvent().getVenue().getName() : null)
                .totalAmount(booking.getTotalAmount())
                .expiresAt(booking.getExpiresAt())
                .coverImageUrl(booking.getEvent().getCoverImageUrl())
                .categoryName(booking.getEvent().getCategory() != null ? booking.getEvent().getCategory().getName() : null)
                .quantity(booking.getTickets().size())
                .build()
        ).collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<com.ticketing.booking.dto.BookingDetailsResponse>> getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Booking not found"));

        if (!booking.getUser().getId().equals(userDetails.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Booking does not belong to the requesting user");
        }

        com.ticketing.booking.dto.BookingDetailsResponse response = com.ticketing.booking.dto.BookingDetailsResponse.builder()
                .id(booking.getId())
                .reference("VVD-" + booking.getId())
                .totalPrice(booking.getTotalAmount())
                .event(com.ticketing.booking.dto.BookingDetailsResponse.EventDto.builder()
                        .title(booking.getEvent().getTitle())
                        .startDate(booking.getEvent().getStartDate())
                        .venueName(booking.getEvent().getVenue() != null ? booking.getEvent().getVenue().getName() : "")
                        .coverImageUrl(booking.getEvent().getCoverImageUrl())
                        .build())
                .tickets(booking.getTickets().stream().map(ticket -> 
                        com.ticketing.booking.dto.BookingDetailsResponse.TicketDto.builder()
                                .id(ticket.getId())
                                .qrCode(ticket.getQrCode())
                                .tierName(ticket.getTier().getTierName())
                                .gate("Main Gate") // Default if not in entity
                                .seat(ticket.getSeatNumber())
                                .code("TCK-" + ticket.getId())
                                .build()
                ).collect(Collectors.toList()))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
