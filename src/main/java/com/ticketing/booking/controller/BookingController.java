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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.booking.dto.BookingDetailsResponse;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.service.BookingIdempotencyService;
import com.ticketing.booking.service.BookingQueryService;
import com.ticketing.booking.service.BookingService;
import com.ticketing.common.dto.ApiResponse;
import com.ticketing.payment.dto.RefundResponse;
import com.ticketing.payment.service.RefundService;
import com.ticketing.user.service.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin REST controller for Booking operations.
 * All business logic and DTO mapping lives in BookingService / BookingQueryService / RefundService.
 *
 * Endpoints:
 *   POST /api/v1/bookings                       — Create reservation (authenticated, Idempotency-Key honoured)
 *   GET  /api/v1/bookings/my                    — Get own bookings (authenticated)
 *   GET  /api/v1/bookings/{id}                  — Get booking detail — tickets/QR gated by state
 *   POST /api/v1/bookings/{bookingId}/check-in  — Check in attendee (ORGANIZER/ADMIN)
 *   POST /api/v1/bookings/{id}/refunds          — Request refund (authenticated, ownership enforced in service)
 *   POST /api/v1/bookings/{id}/sync-payment     — Reconcile a pending payment against Stripe (webhook fallback)
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingIdempotencyService bookingIdempotencyService;
    private final BookingQueryService bookingQueryService;
    private final RefundService refundService;
    private final com.ticketing.payment.service.PaymentReconciliationService paymentReconciliationService;

    /**
     * Fix 8.2: Two guards — @PreAuthorize for role (HTTP layer) + CheckInGuard in state machine (domain layer).
     */
    /**
     * Self-cancel a RESERVED booking. Only the owner may cancel, and only from RESERVED state.
     * Releases inventory back to Redis and the DB.
     */
    @Operation(summary = "Cancel a booking", description = "Self-cancel a RESERVED or PAYMENT_PENDING booking owned by the caller. Releases inventory back to Redis and the DB.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking cancelled")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not in a cancellable state")
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

    @Operation(summary = "Check in an attendee", description = "ORGANIZER/ADMIN only. Transitions a CONFIRMED booking to ATTENDED via the booking state machine.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Attendee checked in")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an ORGANIZER or ADMIN")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not in a check-in-eligible state")
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
    @Operation(summary = "Request a refund", description = "Three-tier refund window: >=7 days before event = full refund, 3-6 days = 50% partial, <3 days = denied.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Refund processed or denial recorded")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller does not own this booking")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
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

    /**
     * Reconciles a booking against Stripe when the confirmation webhook has not arrived.
     *
     * The success redirect carries `session_id`, so the client can ask us to verify
     * directly rather than waiting on an asynchronous signal that may never come.
     * Idempotent and safe to poll: anything already settled returns immediately
     * without touching Stripe.
     */
    @Operation(summary = "Reconcile a pending payment",
            description = "Checks the booking's Stripe session and confirms it if Stripe reports the session paid. "
                    + "Fallback for a missing or delayed checkout.session.completed webhook.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current booking state after reconciliation")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller does not own this booking")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    @PostMapping("/{id}/sync-payment")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> syncPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Payment reconciliation requested for booking {} by user {}",
                correlationId, id, userDetails.getId());

        var state = paymentReconciliationService.reconcile(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(state.name()));
    }

    @Operation(summary = "Reserve tickets", description = "Creates a RESERVED booking and decrements inventory atomically via a Redis Lua floor guard. Hold expires after RESERVATION_TTL_SECONDS.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking reserved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event or ticket tier not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Event not published, or requested quantity exceeds available inventory")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            // Fix 26-idem. required = false so local dev (rate limiting off) still works; when
            // app.rate-limit.enabled=true, RateLimitFilter has already rejected a missing header
            // with a 400 before this method is reached.
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        String correlationId = MDC.get("correlationId");
        log.info("[{}] [booking] Reservation request for event {} tier {} qty {} by user {}",
                correlationId, request.getEventId(), request.getTierId(),
                request.getQuantity(), userDetails.getId());

        Booking booking = bookingIdempotencyService.reserveTickets(
                idempotencyKey,
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

    @Operation(summary = "Get my bookings", description = "Returns all bookings owned by the authenticated user (flat response shape).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of bookings")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
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
    @Operation(summary = "Get booking details", description = "Returns full booking detail including tickets/QR codes, which are only populated when the booking state is CONFIRMED or ATTENDED.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking detail")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller does not own this booking")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookingDetailsResponse>> getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        BookingDetailsResponse response = bookingQueryService.getBookingById(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
