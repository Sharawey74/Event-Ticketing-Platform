package com.ticketing.ticketing_platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.ticketing.event.model.Category;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.CategoryRepository;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.VenueRepository;
import com.ticketing.user.model.User;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TicketingPlatformApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private VenueRepository venueRepository;

	@Autowired
	private EventRepository eventRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void searchEvents_withQueryCategoryAndCity_shouldExcludePastAndUnpublished() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		Instant now = Instant.now();

		Long organizerId = jdbcTemplate.queryForObject(
				"""
				INSERT INTO users (email, password_hash, first_name, last_name, role)
				VALUES (?, ?, ?, ?, CAST(? AS user_role))
				RETURNING id
				""",
				Long.class,
				"search-organizer-" + uniqueSuffix + "@example.com",
				"hashed-password",
				"Search",
				"Organizer",
				"ORGANIZER");

		Category targetCategory = categoryRepository.save(Category.builder()
				.name("Search Category " + uniqueSuffix)
				.description("Target category")
				.iconUrl("target")
				.build());

		Category otherCategory = categoryRepository.save(Category.builder()
				.name("Other Category " + uniqueSuffix)
				.description("Other category")
				.iconUrl("other")
				.build());

		Venue cairoVenue = venueRepository.save(Venue.builder()
				.name("Search Cairo Venue " + uniqueSuffix)
				.address("100 Nile St")
				.city("Cairo")
				.country("EG")
				.totalCapacity(5000)
				.build());

		Venue otherCityVenue = venueRepository.save(Venue.builder()
				.name("Search Dubai Venue " + uniqueSuffix)
				.address("200 Palm St")
				.city("Dubai")
				.country("AE")
				.totalCapacity(5000)
				.build());

		Event expectedEvent = eventRepository.save(Event.builder()
				.title("Rock Night")
				.description("Live music celebration")
				.organizer(User.builder().id(organizerId).build())
				.category(targetCategory)
				.venue(cairoVenue)
				.startDate(now.plus(5, ChronoUnit.DAYS))
				.endDate(now.plus(5, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
				.status(EventStatus.PUBLISHED)
				.dynamicPricingEnabled(Boolean.FALSE)
				.waitlistEnabled(Boolean.FALSE)
				.build());

		eventRepository.save(Event.builder()
				.title("Rock Draft")
				.description("Live music rehearsal")
				.organizer(User.builder().id(organizerId).build())
				.category(targetCategory)
				.venue(cairoVenue)
				.startDate(now.plus(6, ChronoUnit.DAYS))
				.endDate(now.plus(6, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
				.status(EventStatus.DRAFT)
				.dynamicPricingEnabled(Boolean.FALSE)
				.waitlistEnabled(Boolean.FALSE)
				.build());

		eventRepository.save(Event.builder()
				.title("Rock Past")
				.description("Live music archive")
				.organizer(User.builder().id(organizerId).build())
				.category(targetCategory)
				.venue(cairoVenue)
				.startDate(now.minus(2, ChronoUnit.DAYS))
				.endDate(now.minus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
				.status(EventStatus.PUBLISHED)
				.dynamicPricingEnabled(Boolean.FALSE)
				.waitlistEnabled(Boolean.FALSE)
				.build());

		eventRepository.save(Event.builder()
				.title("Rock Other Category")
				.description("Live music event")
				.organizer(User.builder().id(organizerId).build())
				.category(otherCategory)
				.venue(cairoVenue)
				.startDate(now.plus(7, ChronoUnit.DAYS))
				.endDate(now.plus(7, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
				.status(EventStatus.PUBLISHED)
				.dynamicPricingEnabled(Boolean.FALSE)
				.waitlistEnabled(Boolean.FALSE)
				.build());

		eventRepository.save(Event.builder()
				.title("Rock Other City")
				.description("Live music event")
				.organizer(User.builder().id(organizerId).build())
				.category(targetCategory)
				.venue(otherCityVenue)
				.startDate(now.plus(8, ChronoUnit.DAYS))
				.endDate(now.plus(8, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS))
				.status(EventStatus.PUBLISHED)
				.dynamicPricingEnabled(Boolean.FALSE)
				.waitlistEnabled(Boolean.FALSE)
				.build());

		mockMvc.perform(get("/api/search/events")
				.param("q", "music")
				.param("category", targetCategory.getId().toString())
				.param("city", "cairo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].id").value(expectedEvent.getId()))
				.andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"));
	}

}
