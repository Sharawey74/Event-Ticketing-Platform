package com.ticketing.event.dto;

import java.time.Instant;
import java.util.List;

import com.ticketing.event.model.EventStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDetailResponse {

    private Long id;
    private String title;
    private String description;
    private Instant startDate;
    private Instant endDate;
    private Instant salesOpenDate;
    private Instant salesCloseDate;
    private String coverImageUrl;
    private EventStatus status;
    private Boolean dynamicPricingEnabled;
    private Boolean waitlistEnabled;

    private OrganizerInfo organizer;
    private CategoryResponse category;
    private VenueResponse venue;
    private List<TicketTierResponse> ticketTiers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrganizerInfo {
        private Long id;
        private String name;
        private String email;
    }
}
