package com.ticketing.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetailsResponse {
    private Long id;
    private String reference;
    private BigDecimal totalPrice;
    private EventDto event;
    private List<TicketDto> tickets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDto {
        private String title;
        private Instant startDate;
        private String venueName;
        private String coverImageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketDto {
        private Long id;
        private String qrCode;
        private String tierName;
        private String gate;
        private String seat;
        private String code;
    }
}
