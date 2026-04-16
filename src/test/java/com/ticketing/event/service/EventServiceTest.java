package com.ticketing.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.model.Category;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.user.model.User;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    @DisplayName("createEvent with valid data should return event")
    void createEvent_withValidData_shouldReturnEvent() {
        Long organizerId = 7L;
        CreateEventRequest request = createEventRequest(Instant.now().plus(2, ChronoUnit.DAYS));

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(100L);
            return event;
        });

        EventResponse response = eventService.createEvent(request, organizerId);

        assertEquals(100L, response.getId());
        assertEquals(EventStatus.DRAFT, response.getStatus());
        assertEquals(organizerId, response.getOrganizerId());

        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals(EventStatus.DRAFT, eventCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("createEvent with past date should throw validation exception")
    void createEvent_withPastDate_shouldThrowValidationException() {
        CreateEventRequest request = createEventRequest(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThrows(ValidationException.class, () -> eventService.createEvent(request, 7L));
    }

    @Test
    @DisplayName("getEvent with missing id should throw not found")
    void getEvent_withNonExistentId_shouldThrowNotFoundException() {
        when(eventRepository.findByIdWithDetails(404L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> eventService.getEventById(404L));
    }

    @Test
    @DisplayName("updateEvent as non organizer should throw forbidden")
    void updateEvent_asNonOrganizer_shouldThrowForbiddenException() {
        Event existing = createEvent(11L, 99L, EventStatus.DRAFT);
        when(eventRepository.findByIdWithDetails(11L)).thenReturn(Optional.of(existing));

        UpdateEventRequest request = UpdateEventRequest.builder().title("Updated").build();

        assertThrows(AccessDeniedException.class, () -> eventService.updateEvent(11L, request, 7L));
    }

    @Test
    @DisplayName("updateEvent when published should change state")
    void updateEvent_whenPublished_shouldChangeState() {
        Event existing = createEvent(12L, 7L, EventStatus.DRAFT);
        when(eventRepository.findByIdWithDetails(12L)).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateEventRequest request = UpdateEventRequest.builder().status(EventStatus.PUBLISHED).build();

        EventResponse response = eventService.updateEvent(12L, request, 7L);

        assertEquals(EventStatus.PUBLISHED, response.getStatus());
        assertEquals(EventStatus.PUBLISHED, existing.getStatus());
        verify(eventRepository).save(existing);
    }

    @Test
    @DisplayName("deleteEvent when not draft should throw validation exception")
    void deleteEvent_whenNotDraft_shouldThrowValidationException() {
        Event existing = createEvent(13L, 7L, EventStatus.PUBLISHED);
        when(eventRepository.findByIdWithDetails(13L)).thenReturn(Optional.of(existing));

        assertThrows(ValidationException.class, () -> eventService.deleteEvent(13L, 7L));
    }

    @Test
    @DisplayName("publishEvent when draft and organizer should change state")
    void publishEvent_whenDraftAndOrganizer_shouldChangeState() {
        Event existing = createEvent(14L, 7L, EventStatus.DRAFT);
        when(eventRepository.findByIdWithDetails(14L)).thenReturn(Optional.of(existing));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.publishEvent(14L, 7L);

        assertEquals(EventStatus.PUBLISHED, response.getStatus());
        assertEquals(EventStatus.PUBLISHED, existing.getStatus());
        verify(eventRepository).save(existing);
    }

    private CreateEventRequest createEventRequest(Instant startDate) {
        return CreateEventRequest.builder()
            .title("Spring Live")
            .description("Event description")
            .categoryId(3L)
            .venueId(5L)
            .startDate(startDate)
            .endDate(startDate.plus(3, ChronoUnit.HOURS))
            .build();
    }

    private Event createEvent(Long eventId, Long organizerId, EventStatus status) {
        User organizer = User.builder().id(organizerId).build();
        Category category = Category.builder().id(3L).name("Music").build();
        Venue venue = Venue.builder().id(5L).name("Hall").address("A").city("NY").country("US").totalCapacity(100).build();

        return Event.builder()
            .id(eventId)
            .title("Initial")
            .description("Initial description")
            .organizer(organizer)
            .category(category)
            .venue(venue)
            .startDate(Instant.now().plus(2, ChronoUnit.DAYS))
            .endDate(Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
            .status(status)
            .dynamicPricingEnabled(Boolean.FALSE)
            .waitlistEnabled(Boolean.FALSE)
            .build();
    }
}
