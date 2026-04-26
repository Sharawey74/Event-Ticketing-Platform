package com.ticketing.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketing.event.dto.CreateEventRequest;
import com.ticketing.event.dto.EventFilterRequest;
import com.ticketing.event.dto.EventResponse;
import com.ticketing.event.dto.UpdateEventRequest;
import com.ticketing.event.model.Category;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.CategoryRepository;
import com.ticketing.event.repository.VenueRepository;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

@SpringBootTest
@Testcontainers
class EventIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Container
    static GenericContainer<?> rabbitmq = new GenericContainer<>("rabbitmq:4-management").withExposedPorts(5672);

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getFirstMappedPort);
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
    }

    @Autowired
    private EventService eventService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Test
    void createAndRetrieveEvent_shouldIncludeVenueAndCategory() {
        // Arrange
        User organizer = userRepository.save(User.builder()
                .firstName("Org")
                .lastName("Integration")
                .passwordHash("pass")
                .email("org@integration.com")
                .role(com.ticketing.user.model.Role.ORGANIZER)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Test Category")
                .description("Test Description")
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Test Venue")
                .address("123 Test St")
                .city("Test City")
                .country("Test Country")
                .totalCapacity(100)
                .build());

        CreateEventRequest request = CreateEventRequest.builder()
                .title("Integration Test Event")
                .description("Event Description")
                .categoryId(category.getId())
                .venueId(venue.getId())
                .startDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(11, ChronoUnit.DAYS))
                .salesOpenDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .salesCloseDate(Instant.now().plus(9, ChronoUnit.DAYS))
                .build();

        // Act - create
        EventResponse created = eventService.createEvent(request, organizer.getId());
        EventResponse retrieved = eventService.getEventById(created.getId());

        // Assert - create + retrieve
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getTitle()).isEqualTo("Integration Test Event");
        assertThat(retrieved.getCategoryId()).isEqualTo(category.getId());
        assertThat(retrieved.getVenueId()).isEqualTo(venue.getId());
        assertThat(retrieved.getOrganizerId()).isEqualTo(organizer.getId());
        assertThat(retrieved.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void updateEvent_shouldModifyTitleCorrectly() {
        User organizer = userRepository.save(User.builder()
                .firstName("Org2")
                .lastName("Update")
                .passwordHash("pass")
                .email("org2@update.com")
                .role(com.ticketing.user.model.Role.ORGANIZER)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Update Category")
                .description("desc")
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Update Venue")
                .address("456 Update Ave")
                .city("Update City")
                .country("Update Country")
                .totalCapacity(200)
                .build());

        EventResponse created = eventService.createEvent(CreateEventRequest.builder()
                .title("Before Update")
                .categoryId(category.getId())
                .venueId(venue.getId())
                .startDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(11, ChronoUnit.DAYS))
                .build(), organizer.getId());

        UpdateEventRequest updateRequest = new UpdateEventRequest();
        updateRequest.setTitle("After Update");

        EventResponse updated = eventService.updateEvent(created.getId(), updateRequest, organizer.getId());
        assertThat(updated.getTitle()).isEqualTo("After Update");
    }

    @Test
    void publishEvent_shouldChangeStatusToPublished() {
        User organizer = userRepository.save(User.builder()
                .firstName("Org3")
                .lastName("Publish")
                .passwordHash("pass")
                .email("org3@publish.com")
                .role(com.ticketing.user.model.Role.ORGANIZER)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Publish Category")
                .description("desc")
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Publish Venue")
                .address("789 Publish Blvd")
                .city("Publish City")
                .country("Publish Country")
                .totalCapacity(500)
                .build());

        EventResponse created = eventService.createEvent(CreateEventRequest.builder()
                .title("To Be Published")
                .categoryId(category.getId())
                .venueId(venue.getId())
                .startDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(11, ChronoUnit.DAYS))
                .salesOpenDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .salesCloseDate(Instant.now().plus(9, ChronoUnit.DAYS))
                .build(), organizer.getId());

        EventResponse published = eventService.publishEvent(created.getId(), organizer.getId());
        assertThat(published.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void searchEvents_shouldReturnMatchingPublishedEvents() {
        User organizer = userRepository.save(User.builder()
                .firstName("Org4")
                .lastName("Search")
                .passwordHash("pass")
                .email("org4@search.com")
                .role(com.ticketing.user.model.Role.ORGANIZER)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Search Category")
                .description("desc")
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Search Venue")
                .address("101 Search Lane")
                .city("SearchCity")
                .country("SearchCountry")
                .totalCapacity(300)
                .build());

        EventResponse created = eventService.createEvent(CreateEventRequest.builder()
                .title("Searchable Unique Event XYZ")
                .categoryId(category.getId())
                .venueId(venue.getId())
                .startDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .endDate(Instant.now().plus(11, ChronoUnit.DAYS))
                .salesOpenDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .salesCloseDate(Instant.now().plus(9, ChronoUnit.DAYS))
                .build(), organizer.getId());

        eventService.publishEvent(created.getId(), organizer.getId());

        // Search using EventService.getEvents with filter
        EventFilterRequest filter = new EventFilterRequest();
        filter.setStatus(EventStatus.PUBLISHED);
        Page<EventResponse> results = eventService.getEvents(filter, PageRequest.of(0, 10));

        assertThat(results).isNotEmpty();
        assertThat(results.getContent())
                .extracting(EventResponse::getTitle)
                .contains("Searchable Unique Event XYZ");
    }
}
