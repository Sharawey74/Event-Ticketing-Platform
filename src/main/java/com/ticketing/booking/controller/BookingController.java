package com.ticketing.booking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.booking.service.BookingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // Fix 8.2: Two guards for check in - @PreAuthorize (application security) AND CheckInGuard (state machine business logic)
    @PostMapping("/{bookingId}/check-in")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ResponseEntity<Void> checkIn(@PathVariable Long bookingId) {
        bookingService.checkIn(bookingId);
        return ResponseEntity.ok().build();
    }
}
