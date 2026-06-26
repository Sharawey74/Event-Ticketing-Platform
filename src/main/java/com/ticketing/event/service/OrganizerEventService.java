package com.ticketing.event.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.EventStatsProjection;
import com.ticketing.event.dto.AttendeeResponse;
import com.ticketing.event.dto.OrganizerEventResponse;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.repository.EventRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrganizerEventService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizerEventService.class);

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public List<OrganizerEventResponse> getOrganizerEvents(Long organizerId) {
        List<Event> events = eventRepository.findByOrganizerIdWithTiers(organizerId);
        logger.info("Fetched {} events for organizer {}", events.size(), organizerId);

        if (events.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, EventStatsProjection> statsById = bookingRepository.findEventStats(eventIds)
                .stream()
                .collect(Collectors.toMap(EventStatsProjection::getEventId, Function.identity()));

        return events.stream().map(e -> toResponse(e, statsById.get(e.getId()))).toList();
    }

    public List<AttendeeResponse> getEventAttendees(Long eventId, Long organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new AccessDeniedException("You do not own this event");
        }
        List<Booking> bookings = bookingRepository.findConfirmedByEventId(eventId);
        logger.info("Fetched {} attendees for event {} by organizer {}", bookings.size(), eventId, organizerId);
        return bookings.stream().map(this::toAttendeeResponse).toList();
    }

    private AttendeeResponse toAttendeeResponse(Booking booking) {
        String name = booking.getUser().getFirstName() + " " + booking.getUser().getLastName();
        String tierName = booking.getTickets().isEmpty() ? "—"
                : booking.getTickets().get(0).getTier().getTierName();
        return AttendeeResponse.builder()
                .bookingId(booking.getId())
                .attendeeName(name.isBlank() ? booking.getUser().getEmail() : name.trim())
                .email(booking.getUser().getEmail())
                .tierName(tierName)
                .state(booking.getState().name())
                .reference("BKG-" + booking.getId())
                .build();
    }

    private OrganizerEventResponse toResponse(Event event, EventStatsProjection stats) {
        int capacity = event.getTicketTiers() == null ? 0 :
                event.getTicketTiers().stream()
                        .mapToInt(t -> t.getTotalCapacity() == null ? 0 : t.getTotalCapacity())
                        .sum();

        long sold = stats == null ? 0L : stats.getTicketsSold();
        double grossRevenue = (stats == null || stats.getGrossRevenue() == null) ? 0.0
                : stats.getGrossRevenue().doubleValue();
        String status = event.getStatus() == EventStatus.PUBLISHED ? "ACTIVE" : "DRAFT";

        return OrganizerEventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .date(event.getStartDate() == null ? null : event.getStartDate().toString())
                .status(status)
                .sold(sold)
                .capacity(capacity)
                .grossRevenue(grossRevenue)
                .thumbnailUrl(event.getCoverImageUrl())
                .build();
    }
}
