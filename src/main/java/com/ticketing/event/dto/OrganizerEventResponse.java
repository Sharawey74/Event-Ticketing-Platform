package com.ticketing.event.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrganizerEventResponse {
    Long id;
    String title;
    String date;
    String status;
    long sold;
    int capacity;
    double grossRevenue;
    String thumbnailUrl;
}
