package com.ticketing.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private Long id;
    private Long bookingId; // for frontend compatibility
    private String state;
    private String eventTitle;
    private Instant eventDate;
    private String venueName;
    private BigDecimal totalAmount;
    private Instant expiresAt;
    private String coverImageUrl;
    private String categoryName;
    private Integer quantity;
}
