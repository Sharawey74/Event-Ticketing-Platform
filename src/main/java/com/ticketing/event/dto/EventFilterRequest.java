package com.ticketing.event.dto;

import com.ticketing.event.model.EventStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventFilterRequest {

    private EventStatus status;

    private Long categoryId;

    private String city;
}
