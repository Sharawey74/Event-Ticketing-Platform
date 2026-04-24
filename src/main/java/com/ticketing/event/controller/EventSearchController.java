package com.ticketing.event.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.common.dto.ApiResponse;
import com.ticketing.common.util.BusinessConstants;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.service.EventSearchService;

import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/search/events")
@RequiredArgsConstructor
@Validated
public class EventSearchController {

    private static final Logger logger = LoggerFactory.getLogger(EventSearchController.class);

    private final EventSearchService eventSearchService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EventResponse>>> searchEvents(
            @RequestParam(value = "q", required = false)
            @Size(max = BusinessConstants.MAX_SEARCH_PARAM_LENGTH, message = "Search query must be 100 characters or less") String query,
            @RequestParam(value = "category", required = false) Long categoryId,
            @RequestParam(value = "city", required = false)
            @Size(max = BusinessConstants.MAX_SEARCH_PARAM_LENGTH, message = "City filter must be 100 characters or less") String city,
            Pageable pageable) {

        Page<EventResponse> response = eventSearchService.searchEvents(query, categoryId, city, pageable);
        logger.info("Search endpoint finished with query {} category {} city {}", query, categoryId, city);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

