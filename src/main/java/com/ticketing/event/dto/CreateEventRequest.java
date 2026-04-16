package com.ticketing.event.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private Long categoryId;

    @NotNull
    private Long venueId;

    @NotNull
    @Future
    private Instant startDate;

    @NotNull
    private Instant endDate;

    private Instant salesOpenDate;

    private Instant salesCloseDate;

    private String coverImageUrl;
}
