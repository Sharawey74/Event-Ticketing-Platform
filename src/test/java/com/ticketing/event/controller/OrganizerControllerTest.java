package com.ticketing.event.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketing.common.config.TestSecurityConfig;
import com.ticketing.common.security.JwtService;
import com.ticketing.event.dto.AttendeeResponse;
import com.ticketing.event.dto.OrganizerEventResponse;
import com.ticketing.event.service.OrganizerEventService;
import com.ticketing.user.service.CustomUserDetails;

@WebMvcTest(controllers = OrganizerController.class)
@Import(TestSecurityConfig.class)
class OrganizerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private OrganizerEventService organizerEventService;
    @MockitoBean private JwtService jwtService;

    private CustomUserDetails organizerPrincipal;
    private CustomUserDetails userPrincipal;

    @BeforeEach
    void setUp() {
        organizerPrincipal = new CustomUserDetails(
                1L, "organizer@test.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));

        userPrincipal = new CustomUserDetails(
                2L, "user@test.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_ATTENDEE")));
    }

    @Test
    @DisplayName("GET /api/v1/organizer/events: authenticated ORGANIZER receives 200 with event list")
    void getMyEvents_whenOrganizer_shouldReturn200() throws Exception {
        OrganizerEventResponse response = OrganizerEventResponse.builder()
                .id(10L)
                .title("My Event")
                .status("ACTIVE")
                .sold(5L)
                .capacity(100)
                .grossRevenue(500.0)
                .build();

        when(organizerEventService.getOrganizerEvents(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/organizer/events")
                        .with(user(organizerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("My Event"))
                .andExpect(jsonPath("$.data[0].sold").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/organizer/events: unauthenticated user receives 401")
    void getMyEvents_whenUnauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/organizer/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/organizer/events: ATTENDEE role receives 403")
    void getMyEvents_whenAttendeeRole_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/organizer/events")
                        .with(user(userPrincipal)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/organizer/events/{id}/attendees: ORGANIZER receives attendee list")
    void getEventAttendees_whenOrganizer_shouldReturn200() throws Exception {
        AttendeeResponse attendee = AttendeeResponse.builder()
                .bookingId(99L)
                .attendeeName("Jane Doe")
                .email("jane@test.com")
                .tierName("VIP")
                .state("CONFIRMED")
                .reference("BKG-99")
                .build();

        when(organizerEventService.getEventAttendees(anyLong(), anyLong()))
                .thenReturn(List.of(attendee));

        mockMvc.perform(get("/api/v1/organizer/events/5/attendees")
                        .with(user(organizerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attendeeName").value("Jane Doe"))
                .andExpect(jsonPath("$.data[0].tierName").value("VIP"));
    }

    @Test
    @DisplayName("GET /api/v1/organizer/events/{id}/attendees: unauthenticated receives 401")
    void getEventAttendees_whenUnauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/organizer/events/5/attendees"))
                .andExpect(status().isUnauthorized());
    }
}
