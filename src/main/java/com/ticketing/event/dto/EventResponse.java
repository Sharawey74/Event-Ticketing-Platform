package com.ticketing.event.dto;

import java.time.Instant;

import com.ticketing.event.model.EventStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {

    private Long id;

    private String title;

    private String description;

    private Long organizerId;

    private Long categoryId;

    private Long venueId;

    private Instant startDate;

    private Instant endDate;

    private Instant salesOpenDate;

    private Instant salesCloseDate;

    private String coverImageUrl;

    private EventStatus status;

    private Boolean dynamicPricingEnabled;

    private Boolean waitlistEnabled;
}
