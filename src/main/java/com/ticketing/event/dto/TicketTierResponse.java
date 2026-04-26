package com.ticketing.event.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTierResponse {

    private Long id;
    private String tierName;
    private String description;
    private BigDecimal basePrice;
    private Integer totalCapacity;
    private Integer availableCount;
    private Integer maxPerBooking;

}
