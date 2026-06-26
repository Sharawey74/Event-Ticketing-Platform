package com.ticketing.booking.controller;

import java.util.List;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.booking.dto.BookingDetailsResponse;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.service.BookingQueryService;
import com.ticketing.booking.service.BookingService;
import com.ticketing.common.dto.ApiResponse;
import com.ticketing.payment.dto.RefundResponse;
import com.ticketing.payment.service.RefundService;
import com.ticketing.user.service.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin REST controller for Booking operations.
 * All business logic and DTO mapping lives in BookingService / BookingQueryService / RefundService.
 *
 * Endpoints:
 *   POST /api/v1/bookings                       — Create reservation (authenticated)
 *   GET  /api/v1/bookings/my                    — Get own bookings (authenticated)
 *   GET  /api/v1/bookings/{id}                  — Get booking detail — tickets/QR gated by state
 *   POST /api/v1/bookings/{bookingId}/check-in  — Check in attendee (ORGANIZER/ADMIN)
 *   POST /api/v1/bookings/{id}/refunds          — Request refund (authenticated, ownership enforced in service)
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;
    private final BookingQueryService bookingQueryService;
    private final RefundService refundService;

    /**
     * Fix 8.2: Two guards — @PreAuthorize for role (HTTP layer) + CheckInGuard in state machine (domain layer).
     */
    /**
     * Self-cancel a RESERVED booking. Only the owner may cancel, and only from RESERVED state.
     * Releases inventory back to Redis and the DB.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Self-cancel requested for booking {} by user {}",
                correlationId, id, userDetails.getId());

        bookingService.cancelBooking(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{bookingId}/check-in")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<Void> checkIn(@PathVariable Long bookingId) {
        bookingService.checkIn(bookingId);
        return ResponseEntity.ok().build();
    }

    /**
     * Three-tier refund window:
     *   >= 7 days before event  -> full refund
     *   3-6 days before event   -> 50% partial refund
     *   < 3 days before event   -> denied (reason stored on booking, Fix 12.1)
     *
     * Ownership validated inside RefundService.
     */
    @PostMapping("/{id}/refunds")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RefundResponse>> requestRefund(
            @PathVariable Long id,
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
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Reservation request for event {} tier {} qty {} by user {}",
                correlationId, request.getEventId(), request.getTierId(),
                request.getQuantity(), userDetails.getId());

        Booking booking = bookingService.reserveTickets(
                userDetails.getId(),
                request.getEventId(),
                request.getTierId(),
                request.getQuantity());

        BookingResponse response = BookingResponse.builder()
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
                .quantity(request.getQuantity())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<BookingResponse> response = bookingQueryService.getMyBookings(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Fix 5.2 / Fix 2.7: Tickets and QR codes are only present in the response when
     * booking state is CONFIRMED or ATTENDED. All other states return an empty ticket list.
     * State gate is enforced in BookingQueryService.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingDetailsResponse>> getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        BookingDetailsResponse response = bookingQueryService.getBookingById(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
