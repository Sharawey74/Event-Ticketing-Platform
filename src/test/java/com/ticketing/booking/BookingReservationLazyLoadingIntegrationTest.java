package com.ticketing.booking;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.booking.dto.CreateBookingRequest;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.event.model.Category;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.CategoryRepository;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.VenueRepository;
import com.ticketing.inventory.service.InventoryService;
import com.ticketing.ticketing_platform.TestcontainersConfiguration;
import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;
import com.ticketing.user.service.CustomUserDetails;

/**
 * Reproduces a production-only bug (Bug 1, Day 23): BookingService.reserveTickets() loaded
 * Event via a plain findById() with no fetch graph. With spring.jpa.open-in-view=false (the
 * real production setting — application-prod.yml), the Hibernate session closes when the
 * @Transactional service method returns, so BookingController's later access to
 * booking.getEvent().getVenue()/.getCategory() throws LazyInitializationException, which
 * GlobalExceptionHandler's catch-all turns into an HTTP 500 — even though the booking row
 * had already committed successfully. Locally, spring.jpa.open-in-view defaults to true,
 * which is why this never reproduced outside production. This test forces open-in-view=false
 * to match prod and drives the real, non-mocked BookingController end-to-end via MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class BookingReservationLazyLoadingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketTierRepository ticketTierRepository;

    @Autowired
    private InventoryService inventoryService;

    @Test
    @DisplayName("POST /api/v1/bookings with spring.jpa.open-in-view=false (matches prod) "
            + "should return 200 with venue/category names, not a 500 (Bug 1 fix)")
    void createBooking_withOpenInViewDisabled_shouldReturnReservedBookingNotServerError() throws Exception {
        User attendee = userRepository.save(User.builder()
                .firstName("Lazy").lastName("Loader")
                .email("lazy-loading-" + UUID.randomUUID() + "@test.com")
                .passwordHash("irrelevant-not-checked-for-token-auth")
                .role(Role.USER)
                .build());

        User organizer = userRepository.save(User.builder()
                .firstName("Lazy").lastName("Organizer")
                .email("lazy-organizer-" + UUID.randomUUID() + "@test.com")
                .passwordHash("irrelevant-not-checked-for-token-auth")
                .role(Role.ORGANIZER)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Lazy Loading Category " + UUID.randomUUID())
                .description("Fixture category for the OSIV reservation regression test")
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Lazy Loading Venue")
                .address("1 Test St")
                .city("Test City")
                .country("Test Country")
                .totalCapacity(500)
                .build());

        Event event = eventRepository.save(Event.builder()
                .title("Lazy Loading Regression Event")
                .organizer(organizer)
                .category(category)
                .venue(venue)
                .status(EventStatus.PUBLISHED)
                .startDate(Instant.now().plusSeconds(86400L * 30))
                .endDate(Instant.now().plusSeconds(86400L * 31))
                .build());

        TicketTier tier = ticketTierRepository.save(TicketTier.builder()
                .event(event)
                .tierName("GA")
                .basePrice(BigDecimal.valueOf(25))
                .totalCapacity(100)
                .availableCount(100)
                .maxPerBooking(10)
                .build());

        inventoryService.setAvailableCount(tier.getId(), tier.getAvailableCount());

        CustomUserDetails principal = new CustomUserDetails(attendee.getId(), attendee.getEmail(),
                "irrelevant", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        CreateBookingRequest request = new CreateBookingRequest();
        request.setEventId(event.getId());
        request.setTierId(tier.getId());
        request.setQuantity(1);

        mockMvc.perform(post("/api/v1/bookings")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("RESERVED"))
                .andExpect(jsonPath("$.data.venueName").value(venue.getName()))
                .andExpect(jsonPath("$.data.categoryName").value(category.getName()));
    }
}
