package com.ticketing.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.BookingState;
import com.ticketing.booking.model.Ticket;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.EventStatsProjection;
import com.ticketing.event.dto.AttendeeResponse;
import com.ticketing.event.dto.OrganizerEventResponse;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class OrganizerEventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private BookingRepository bookingRepository;

    @InjectMocks private OrganizerEventService organizerEventService;

    private User organizer;
    private Event event;
    private TicketTier tier;

    @BeforeEach
    void setUp() {
        organizer = User.builder()
                .id(1L)
                .email("organizer@test.com")
                .role(Role.ORGANIZER)
                .build();

        tier = TicketTier.builder()
                .id(10L)
                .tierName("VIP")
                .basePrice(BigDecimal.valueOf(100))
                .totalCapacity(200)
                .availableCount(150)
                .build();

        event = Event.builder()
                .id(5L)
                .title("Rock Concert")
                .status(EventStatus.PUBLISHED)
                .organizer(organizer)
                .startDate(Instant.now().plusSeconds(86400))
                .ticketTiers(List.of(tier))
                .build();
    }

    @Test
    @DisplayName("getOrganizerEvents: should return mapped response list when events exist")
    void getOrganizerEvents_whenEventsExist_shouldReturnMappedResponses() {
        EventStatsProjection stats = statsProjection(5L, 50L, BigDecimal.valueOf(5000));
        when(eventRepository.findByOrganizerIdWithTiers(1L)).thenReturn(List.of(event));
        when(bookingRepository.findEventStats(List.of(5L))).thenReturn(List.of(stats));

        List<OrganizerEventResponse> result = organizerEventService.getOrganizerEvents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Rock Concert");
        assertThat(result.get(0).getSold()).isEqualTo(50L);
        assertThat(result.get(0).getGrossRevenue()).isEqualTo(5000.0);
        assertThat(result.get(0).getCapacity()).isEqualTo(200);
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getOrganizerEvents: should return empty list when organizer has no events")
    void getOrganizerEvents_whenNoEvents_shouldReturnEmptyList() {
        when(eventRepository.findByOrganizerIdWithTiers(1L)).thenReturn(List.of());

        List<OrganizerEventResponse> result = organizerEventService.getOrganizerEvents(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getOrganizerEvents: should handle null stats and null tiers gracefully")
    void getOrganizerEvents_whenStatsNull_shouldDefaultToZeroValues() {
        Event draftEvent = Event.builder()
                .id(6L)
                .title("Draft Event")
                .status(EventStatus.DRAFT)
                .organizer(organizer)
                .ticketTiers(null)
                .build();
        when(eventRepository.findByOrganizerIdWithTiers(1L)).thenReturn(List.of(draftEvent));
        when(bookingRepository.findEventStats(any())).thenReturn(List.of());

        List<OrganizerEventResponse> result = organizerEventService.getOrganizerEvents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSold()).isEqualTo(0L);
        assertThat(result.get(0).getGrossRevenue()).isEqualTo(0.0);
        assertThat(result.get(0).getCapacity()).isEqualTo(0);
        assertThat(result.get(0).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("getEventAttendees: should return attendee list when organizer owns event")
    void getEventAttendees_whenOwnerRequests_shouldReturnAttendees() {
        User attendeeUser = User.builder()
                .id(2L)
                .email("attendee@test.com")
                .firstName("Jane")
                .lastName("Doe")
                .build();

        Booking booking = Booking.builder()
                .id(99L)
                .user(attendeeUser)
                .state(BookingState.CONFIRMED)
                .tickets(new ArrayList<>())
                .build();
        booking.getTickets().add(Ticket.builder()
                .id(1L)
                .booking(booking)
                .tier(tier)
                .build());

        when(eventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(bookingRepository.findConfirmedByEventId(5L)).thenReturn(List.of(booking));

        List<AttendeeResponse> result = organizerEventService.getEventAttendees(5L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("attendee@test.com");
        assertThat(result.get(0).getAttendeeName()).isEqualTo("Jane Doe");
        assertThat(result.get(0).getTierName()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("getEventAttendees: should throw EntityNotFoundException when event not found")
    void getEventAttendees_whenEventNotFound_shouldThrowEntityNotFoundException() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizerEventService.getEventAttendees(99L, 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("getEventAttendees: should throw AccessDeniedException when not event owner")
    void getEventAttendees_whenNotOwner_shouldThrowAccessDeniedException() {
        when(eventRepository.findById(5L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> organizerEventService.getEventAttendees(5L, 999L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getEventAttendees: attendee name falls back to email when name is blank")
    void getEventAttendees_whenNameBlank_shouldFallBackToEmail() {
        User noNameUser = User.builder()
                .id(3L)
                .email("noname@test.com")
                .firstName("")
                .lastName("")
                .build();

        Booking booking = Booking.builder()
                .id(100L)
                .user(noNameUser)
                .state(BookingState.CONFIRMED)
                .tickets(new ArrayList<>())
                .build();

        when(eventRepository.findById(5L)).thenReturn(Optional.of(event));
        when(bookingRepository.findConfirmedByEventId(5L)).thenReturn(List.of(booking));

        List<AttendeeResponse> result = organizerEventService.getEventAttendees(5L, 1L);

        assertThat(result.get(0).getAttendeeName()).isEqualTo("noname@test.com");
        assertThat(result.get(0).getTierName()).isEqualTo("—");
    }

    private EventStatsProjection statsProjection(Long eventId, Long sold, BigDecimal revenue) {
        return new EventStatsProjection() {
            public Long getEventId() { return eventId; }
            public Long getTicketsSold() { return sold; }
            public BigDecimal getGrossRevenue() { return revenue; }
        };
    }
}
