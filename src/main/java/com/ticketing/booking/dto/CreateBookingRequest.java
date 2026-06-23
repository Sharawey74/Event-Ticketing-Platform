package com.ticketing.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateBookingRequest {
    @NotNull
    private Long eventId;
    
    @NotNull
    private Long tierId;
    
    @NotNull
    @Min(1)
    private Integer quantity;
}
