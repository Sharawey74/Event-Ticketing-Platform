package com.ticketing.event.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AttendeeResponse {
    Long bookingId;
    String attendeeName;
    String email;
    String tierName;
    String state;
    String reference;
}
