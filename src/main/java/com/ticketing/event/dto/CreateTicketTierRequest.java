package com.ticketing.event.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketTierRequest {

    @NotBlank
    private String tierName;

    private String description;

    @NotNull
    @Min(0)
    private BigDecimal basePrice;

    @NotNull
    @Min(1)
    private Integer totalCapacity;

    @Min(1)
    private Integer maxPerBooking;
}
