package com.ticketing.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketing.common.config.TestSecurityConfig;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.service.EventSearchService;

@WebMvcTest(controllers = EventSearchController.class)
@Import(TestSecurityConfig.class)
class EventSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventSearchService eventSearchService;

    @MockitoBean
    private com.ticketing.common.security.JwtService jwtService;

    @Test
    @DisplayName("GET /api/search/events with valid filters should return 200 with paginated results")
    void searchEvents_withValidFilters_shouldReturn200WithResults() throws Exception {
        EventResponse event = EventResponse.builder()
                .id(1L)
                .title("Music Festival")
                .description("Summer music event")
                .organizerId(7L)
                .categoryId(3L)
                .venueId(5L)
                .startDate(Instant.now().plusSeconds(3600))
                .endDate(Instant.now().plusSeconds(7200))
                .status(EventStatus.PUBLISHED)
                .dynamicPricingEnabled(Boolean.FALSE)
                .waitlistEnabled(Boolean.FALSE)
                .build();

        when(eventSearchService.searchEvents(eq("music"), eq(3L), eq("Cairo"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        mockMvc.perform(get("/api/search/events")
                .param("q", "music")
                .param("category", "3")
                .param("city", "Cairo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Music Festival"));
    }

    @Test
    @DisplayName("GET /api/search/events with no params should return 200 with empty results")
    void searchEvents_withNoParams_shouldReturn200() throws Exception {
        when(eventSearchService.searchEvents(eq(null), eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/search/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/search/events with keyword exceeding 100 chars should return 400 Bad Request")
    void searchEvents_withOversizedKeyword_shouldReturn400() throws Exception {
        String oversizedQuery = "a".repeat(101);

        mockMvc.perform(get("/api/search/events")
                .param("q", oversizedQuery))
                .andExpect(status().isBadRequest());
    }
}
