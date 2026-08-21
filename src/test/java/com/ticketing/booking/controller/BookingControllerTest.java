package com.ticketing.booking.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.booking.dto.BookingDetailsResponse;
import com.ticketing.booking.dto.BookingResponse;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.service.BookingQueryService;
import com.ticketing.booking.service.BookingService;
import com.ticketing.common.config.TestSecurityConfig;
import com.ticketing.common.exception.ConflictException;
import com.ticketing.common.security.JwtService;
import com.ticketing.payment.service.RefundService;
import com.ticketing.user.service.CustomUserDetails;
import org.springframework.security.access.AccessDeniedException;

@WebMvcTest(controllers = BookingController.class)
@Import(TestSecurityConfig.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private BookingQueryService bookingQueryService;

    @MockitoBean
    private RefundService refundService;

    // BookingController constructor-injects this, and @WebMvcTest still builds the
    // controller bean — without a mock the whole slice fails to load.
    @MockitoBean
    private com.ticketing.payment.service.PaymentReconciliationService paymentReconciliationService;

    @MockitoBean
    private JwtService jwtService;

    private CustomUserDetails mockUser;
    private CustomUserDetails mockOrganizer;

    @BeforeEach
    void setUp() {
        mockUser = new CustomUserDetails(1L, "user@eventora.com", "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        mockOrganizer = new CustomUserDetails(2L, "organizer@eventora.com", "password",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
    }

    // ── GET /api/v1/bookings/my ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /my when authenticated should return booking list")
    void getMyBookings_whenAuthenticated_shouldReturnBookingList() throws Exception {
        BookingResponse bookingResponse = BookingResponse.builder()
                .id(1L)
                .bookingId(1L)
                .state("RESERVED")
                .eventTitle("Cairo Jazz Night")
                .totalAmount(BigDecimal.valueOf(500))
                .build();

        when(bookingQueryService.getMyBookings(1L)).thenReturn(List.of(bookingResponse));

        mockMvc.perform(get("/api/v1/bookings/my").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].state").value("RESERVED"))
                .andExpect(jsonPath("$.data[0].eventTitle").value("Cairo Jazz Night"));
    }

    @Test
    @DisplayName("GET /my when unauthenticated should return 401")
    void getMyBookings_whenUnauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/my"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/v1/bookings/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("GET /{id} for CONFIRMED booking should return tickets with QR via service")
    void getBookingById_whenConfirmed_shouldReturnTicketsFromService() throws Exception {
        BookingDetailsResponse.TicketDto ticket = BookingDetailsResponse.TicketDto.builder()
                .id(10L)
                .qrCode("qr-abc123")
                .tierName("VIP")
                .build();

        BookingDetailsResponse detailResponse = BookingDetailsResponse.builder()
                .id(1L)
                .reference("EVT-1")
                .totalPrice(BigDecimal.valueOf(900))
                .event(BookingDetailsResponse.EventDto.builder()
                        .title("Cairo Jazz Night")
                        .startDate(Instant.now())
                        .venueName("Cairo Opera House")
                        .build())
                .tickets(List.of(ticket))
                .build();

        when(bookingQueryService.getBookingById(1L, 1L)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/bookings/1").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tickets[0].qrCode").value("qr-abc123"))
                .andExpect(jsonPath("$.data.event.title").value("Cairo Jazz Night"));
    }

    @Test
    @DisplayName("GET /{id} for RESERVED booking should return empty ticket list via service")
    void getBookingById_whenReserved_shouldReturnEmptyTicketList() throws Exception {
        BookingDetailsResponse detailResponse = BookingDetailsResponse.builder()
                .id(1L)
                .reference("EVT-1")
                .totalPrice(BigDecimal.valueOf(500))
                .event(BookingDetailsResponse.EventDto.builder()
                        .title("Test Event")
                        .startDate(Instant.now())
                        .venueName("Venue")
                        .build())
                .tickets(List.of())
                .build();

        when(bookingQueryService.getBookingById(1L, 1L)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/bookings/1").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tickets").isEmpty());
    }

    @Test
    @DisplayName("GET /{id} when unauthenticated should return 401")
    void getBookingById_whenUnauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/bookings ───────────────────────────────────────────────

    @Test
    @DisplayName("POST / with valid request should return RESERVED booking")
    void createBooking_whenValid_shouldReturnReservedBooking() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId(10L);
        request.setTierId(5L);
        request.setQuantity(2);

        com.ticketing.event.model.Event event = com.ticketing.event.model.Event.builder()
                .id(10L)
                .title("Test Event")
                .startDate(Instant.now())
                .build();

        Booking mockBooking = Booking.builder()
                .id(1L)
                .state(BookingState.RESERVED)
                .totalAmount(BigDecimal.valueOf(1000))
                .tickets(List.of())
                .build();
        mockBooking.setEvent(event);

        when(bookingService.reserveTickets(anyLong(), anyLong(), anyLong(), anyInt()))
                .thenReturn(mockBooking);

        mockMvc.perform(post("/api/v1/bookings")
                .with(user(mockUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("RESERVED"));
    }

    @Test
    @DisplayName("POST / when unauthenticated should return 401")
    void createBooking_whenUnauthenticated_shouldReturn401() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId(10L);
        request.setTierId(5L);
        request.setQuantity(2);

        mockMvc.perform(post("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /{bookingId}/check-in ──────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/check-in when ORGANIZER should return 200")
    void checkIn_whenOrganizer_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/1/check-in").with(user(mockOrganizer)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /{id}/check-in when USER (not organizer) should return 403")
    void checkIn_whenNormalUser_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/1/check-in"))
                .andExpect(status().isForbidden());
    }

    // ── DELETE /api/v1/bookings/{id} — self-cancel ──────────────────────────

    @Test
    @DisplayName("DELETE /{id} when owner of RESERVED booking should return 200")
    void cancelBooking_whenReservedOwner_shouldReturn200() throws Exception {
        doNothing().when(bookingService).cancelBooking(1L, 1L);

        mockMvc.perform(delete("/api/v1/bookings/1").with(user(mockUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /{id} when not owner should return 403")
    void cancelBooking_whenNotOwner_shouldReturn403() throws Exception {
        doThrow(new AccessDeniedException("You do not own this booking"))
                .when(bookingService).cancelBooking(1L, 1L);

        mockMvc.perform(delete("/api/v1/bookings/1").with(user(mockUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /{id} when booking already CONFIRMED should return 409")
    void cancelBooking_whenAlreadyConfirmed_shouldReturn409() throws Exception {
        doThrow(new ConflictException("Only RESERVED bookings can be self-cancelled. Current state: CONFIRMED"))
                .when(bookingService).cancelBooking(1L, 1L);

        mockMvc.perform(delete("/api/v1/bookings/1").with(user(mockUser)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /{id} when unauthenticated should return 401")
    void cancelBooking_whenUnauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(delete("/api/v1/bookings/1"))
                .andExpect(status().isUnauthorized());
    }
}
