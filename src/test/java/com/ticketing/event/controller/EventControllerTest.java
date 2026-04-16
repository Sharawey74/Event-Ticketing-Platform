package com.ticketing.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.common.security.JwtService;
import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventFilterRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.service.EventService;
import com.ticketing.user.service.AuthService;

@WebMvcTest(controllers = EventController.class)
@AutoConfigureMockMvc(addFilters = false)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    private static final TestingAuthenticationToken AUTH_TOKEN = new TestingAuthenticationToken("org@example.com", null, "ROLE_ORGANIZER");

    @Test
    @DisplayName("POST /api/events should create event and return success")
    void createEvent_shouldReturnSuccess() throws Exception {
        CreateEventRequest request = CreateEventRequest.builder()
            .title("Tech Conference")
            .categoryId(1L)
            .venueId(1L)
            .startDate(Instant.now().plus(10, ChronoUnit.DAYS))
            .endDate(Instant.now().plus(12, ChronoUnit.DAYS))
            .build();

        EventResponse response = EventResponse.builder()
            .id(1L)
            .title("Tech Conference")
            .organizerId(7L)
            .status(EventStatus.DRAFT)
            .build();

        when(authService.getUserIdByEmail("org@example.com")).thenReturn(7L);
        when(eventService.createEvent(any(CreateEventRequest.class), eq(7L))).thenReturn(response);

        mockMvc.perform(post("/api/events")
                .principal(AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.title").value("Tech Conference"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /api/events/{id} should return event")
    void getEvent_shouldReturnEvent() throws Exception {
        EventResponse response = EventResponse.builder()
            .id(1L)
            .title("Tech Conference")
            .status(EventStatus.PUBLISHED)
            .build();

        when(eventService.getEventById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/events/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /api/events should return paginated events")
    void getEvents_shouldReturnPaginatedEvents() throws Exception {
        EventResponse response = EventResponse.builder()
            .id(1L)
            .title("Tech Conference")
            .status(EventStatus.PUBLISHED)
            .build();

        when(eventService.getEvents(any(EventFilterRequest.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/events")
                .param("status", "PUBLISHED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    @Test
    @DisplayName("PUT /api/events/{id} should return updated event")
    void updateEvent_shouldReturnUpdatedEvent() throws Exception {
        UpdateEventRequest request = new UpdateEventRequest();
        request.setTitle("Updated Tech Conference");

        EventResponse response = EventResponse.builder()
            .id(1L)
            .title("Updated Tech Conference")
            .build();

        when(authService.getUserIdByEmail("org@example.com")).thenReturn(7L);
        when(eventService.updateEvent(eq(1L), any(UpdateEventRequest.class), eq(7L))).thenReturn(response);

        mockMvc.perform(put("/api/events/1")
                .principal(AUTH_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("Updated Tech Conference"));
    }

    @Test
    @DisplayName("DELETE /api/events/{id} should return success")
    void deleteEvent_shouldReturnSuccess() throws Exception {
        when(authService.getUserIdByEmail("org@example.com")).thenReturn(7L);

        mockMvc.perform(delete("/api/events/1")
                .principal(AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/events/{id}/publish should return published event")
    void publishEvent_shouldReturnPublishedEvent() throws Exception {
        EventResponse response = EventResponse.builder()
            .id(1L)
            .title("Tech Conference")
            .status(EventStatus.PUBLISHED)
            .build();

        when(authService.getUserIdByEmail("org@example.com")).thenReturn(7L);
        when(eventService.publishEvent(1L, 7L)).thenReturn(response);

        mockMvc.perform(post("/api/events/1/publish")
                .principal(AUTH_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }
}
