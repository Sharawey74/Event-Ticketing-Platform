package com.ticketing.event.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.model.Category;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.user.model.User;

@ExtendWith(MockitoExtension.class)
class EventSearchServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventSearchService eventSearchService;

    @Test
    @DisplayName("searchEvents with filters should return mapped paginated result")
    void searchEvents_withFilters_shouldReturnMappedPaginatedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        Event event = Event.builder()
                .id(1L)
                .title("Music Festival")
                .description("Summer music event")
                .organizer(User.builder().id(7L).build())
                .category(Category.builder().id(3L).build())
                .venue(Venue.builder().id(5L).build())
                .startDate(Instant.now().plusSeconds(3_600))
                .endDate(Instant.now().plusSeconds(7_200))
                .status(EventStatus.PUBLISHED)
                .dynamicPricingEnabled(Boolean.FALSE)
                .waitlistEnabled(Boolean.FALSE)
                .build();

        when(eventRepository.searchPublishedEvents(
                eq("music"),
                eq(3L),
                eq("Cairo"),
                any(Instant.class),
                eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<EventResponse> response = eventSearchService.searchEvents("music", 3L, "Cairo", pageable);

        assertEquals(1, response.getTotalElements());
        assertEquals("Music Festival", response.getContent().getFirst().getTitle());
        verify(eventRepository).searchPublishedEvents(eq("music"), eq(3L), eq("Cairo"), any(Instant.class),
                eq(pageable));
    }

    @Test
    @DisplayName("searchEvents without filters should return empty page")
    void searchEvents_withoutFilters_shouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(eventRepository.searchPublishedEvents(
                eq(null),
                eq(null),
                eq(null),
                any(Instant.class),
                eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<EventResponse> response = eventSearchService.searchEvents(null, null, null, pageable);

        assertEquals(0, response.getTotalElements());
        verify(eventRepository).searchPublishedEvents(eq(null), eq(null), eq(null), any(Instant.class), eq(pageable));
    }

    @Test
    @DisplayName("searchEvents with blank query should normalize to null")
    void searchEvents_withBlankQuery_shouldNormalizeToNull() {
        Pageable pageable = PageRequest.of(0, 10);
        when(eventRepository.searchPublishedEvents(
                eq(null),
                eq(null),
                eq(null),
                any(Instant.class),
                eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<EventResponse> response = eventSearchService.searchEvents("   ", null, null, pageable);

        assertEquals(0, response.getTotalElements());
        verify(eventRepository).searchPublishedEvents(eq(null), eq(null), eq(null), any(Instant.class), eq(pageable));
    }
}
