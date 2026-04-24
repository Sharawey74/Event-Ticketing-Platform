package com.ticketing.event.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVenueRequest {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(min = 5, max = 255, message = "Address must be between 5 and 255 characters")
    private String address;

    @Size(min = 2, max = 100, message = "City must be between 2 and 100 characters")
    private String city;

    @Size(min = 2, max = 100, message = "Country must be between 2 and 100 characters")
    private String country;

    private Integer capacity;
}
