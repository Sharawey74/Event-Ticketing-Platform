package com.ticketing.event.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.common.config.TestSecurityConfig;
import com.ticketing.event.dto.CreateVenueRequest;
import com.ticketing.event.dto.VenueResponse;
import com.ticketing.event.service.VenueService;

@WebMvcTest(controllers = VenueController.class)
@Import(TestSecurityConfig.class)
class VenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private com.ticketing.common.security.JwtService jwtService;

    // ─── ADMIN happy-path ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /api/venues as ADMIN should create venue and return 200")
    void createVenue_asAdmin_shouldReturn200() throws Exception {
        CreateVenueRequest request = CreateVenueRequest.builder()
                .name("Grand Arena")
                .address("100 Main St")
                .city("New York")
                .country("US")
                .totalCapacity(20000)
                .build();

        VenueResponse response = VenueResponse.builder()
                .id(10L)
                .name("Grand Arena")
                .address("100 Main St")
                .city("New York")
                .country("US")
                .totalCapacity(20000)
                .build();

        when(venueService.createVenue(any(CreateVenueRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/venues").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /api/venues/{id} as ADMIN should update venue and return 200")
    void updateVenue_asAdmin_shouldReturn200() throws Exception {
        CreateVenueRequest request = CreateVenueRequest.builder()
                .name("Updated Arena")
                .address("999 Updated St")
                .city("Boston")
                .country("US")
                .totalCapacity(30000)
                .build();

        VenueResponse response = VenueResponse.builder()
                .id(10L)
                .name("Updated Arena")
                .build();

        when(venueService.updateVenue(any(Long.class), any(CreateVenueRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/venues/10").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Updated Arena"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/venues/{id} as ADMIN should delete venue and return 200")
    void deleteVenue_asAdmin_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/venues/10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── ORGANIZER denied (403) ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("POST /api/venues as ORGANIZER should return 403 Forbidden")
    void createVenue_asOrganizer_shouldReturn403() throws Exception {
        CreateVenueRequest request = CreateVenueRequest.builder()
                .name("Rogue Venue")
                .address("1 Hacker Lane")
                .city("Nowhere")
                .country("US")
                .totalCapacity(100)
                .build();

        mockMvc.perform(post("/api/venues").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("DELETE /api/venues/{id} as ORGANIZER should return 403 Forbidden")
    void deleteVenue_asOrganizer_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/venues/10").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ─── Unauthenticated (401) ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/venues without authentication should return 401 Unauthorized")
    void createVenue_unauthenticated_shouldReturn401() throws Exception {
        CreateVenueRequest request = CreateVenueRequest.builder()
                .name("Anon Venue")
                .address("0 Ghost St")
                .city("Nowhere")
                .country("US")
                .totalCapacity(50)
                .build();

        mockMvc.perform(post("/api/venues").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ─── Public GET endpoints ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/venues is public and should return 200 without authentication")
    void listVenues_withoutAuth_shouldReturn200() throws Exception {
        when(venueService.listVenues(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
