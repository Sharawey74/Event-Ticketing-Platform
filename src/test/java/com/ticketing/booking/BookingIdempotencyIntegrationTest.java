package com.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketing.booking.model.Booking;
import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.booking.service.BookingIdempotencyService;
import com.ticketing.booking.service.BookingService;
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

/**
 * Fix 26-idem — proves the idempotency guarantee against a real PostgreSQL, not a mock.
 *
 * <p>The unit tests in {@code BookingIdempotencyServiceTest} stub
 * {@code DataIntegrityViolationException}, so they verify the <em>handling</em> of a collision but
 * assume the collision happens. This test removes that assumption: it drives the real service
 * against the real schema and checks that {@code uq_bookings_idempotency_key} (V14) actually
 * refuses the second insert, and that the seat is held exactly once.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}: the first reservation has to genuinely commit
 * for the second call to see it. A test transaction would roll everything back and hide the very
 * behaviour under test. Fixtures are uniquely named per run instead of being cleaned up.
 */
@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
class BookingIdempotencyIntegrationTest {

    @Autowired
    private BookingIdempotencyService bookingIdempotencyService;

    /** Used only to bypass the wrapper's fast path and hit the constraint directly. */
    @Autowired
    private BookingService bookingService;

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
    @DisplayName("Replaying an Idempotency-Key returns the SAME booking and holds the seats only once")
    void reserveTickets_whenSameKeyReplayed_shouldReturnSameBookingAndHoldSeatsOnce() {
        Fixture f = createFixture();
        String idempotencyKey = UUID.randomUUID().toString();

        Booking first = bookingIdempotencyService.reserveTickets(
                idempotencyKey, f.userId, f.eventId, f.tierId, 2);

        // The retry a timed-out client would send: identical key, identical body.
        Booking replay = bookingIdempotencyService.reserveTickets(
                idempotencyKey, f.userId, f.eventId, f.tierId, 2);

        assertThat(replay.getId())
                .as("a replay must return the original booking, not create a second one")
                .isEqualTo(first.getId());

        // The point of the whole fix: one hold, not two. Two bookings would mean two Stripe
        // sessions for the same intent -- the shape of the double charge on booking 562.
        TicketTier tier = ticketTierRepository.findById(f.tierId).orElseThrow();
        assertThat(tier.getAvailableCount())
                .as("seats must be decremented once, not twice")
                .isEqualTo(98);
        assertThat(inventoryService.getAvailableCount(f.tierId))
                .as("the Redis counter must also be decremented once")
                .isEqualTo(98);
    }

    @Test
    @DisplayName("The UNIQUE constraint itself refuses a duplicate key — the guarantee does not rest on the fast-path lookup")
    void reserveTickets_whenFastPathBypassed_shouldBeRefusedByTheDatabaseConstraint() {
        Fixture f = createFixture();
        String idempotencyKey = UUID.randomUUID().toString();

        bookingService.reserveTickets(f.userId, f.eventId, f.tierId, 1, idempotencyKey);

        // Straight at BookingService, deliberately skipping BookingIdempotencyService's
        // "have I seen this key?" read. That read is an optimisation; two genuinely concurrent
        // requests would both sail past it. This asserts what is left when it does not help --
        // uq_bookings_idempotency_key (V14) refusing the second INSERT.
        //
        // If this test ever fails, the whole fix is inert: the column would be populated but
        // unconstrained, and duplicates would be created silently.
        assertThatThrownBy(() -> bookingService.reserveTickets(f.userId, f.eventId, f.tierId, 1, idempotencyKey))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Distinct Idempotency-Keys still create distinct bookings — the guard must not over-block")
    void reserveTickets_whenDifferentKeys_shouldCreateSeparateBookings() {
        Fixture f = createFixture();

        Booking first = bookingIdempotencyService.reserveTickets(
                UUID.randomUUID().toString(), f.userId, f.eventId, f.tierId, 1);
        Booking second = bookingIdempotencyService.reserveTickets(
                UUID.randomUUID().toString(), f.userId, f.eventId, f.tierId, 1);

        assertThat(second.getId()).isNotEqualTo(first.getId());

        TicketTier tier = ticketTierRepository.findById(f.tierId).orElseThrow();
        assertThat(tier.getAvailableCount()).isEqualTo(98);
    }

    @Test
    @DisplayName("A null key creates a booking per call — un-keyed rows must not collide under the UNIQUE constraint")
    void reserveTickets_whenNoKey_shouldCreateSeparateBookingsAndNotCollideOnNull() {
        Fixture f = createFixture();

        // Two NULL idempotency_key rows in the same table. PostgreSQL treats NULLs as distinct
        // under UNIQUE; if that were not true, the second reservation here would blow up and
        // every pre-V14 booking path would have broken the moment the constraint was added.
        Booking first = bookingIdempotencyService.reserveTickets(null, f.userId, f.eventId, f.tierId, 1);
        Booking second = bookingIdempotencyService.reserveTickets(null, f.userId, f.eventId, f.tierId, 1);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(first.getIdempotencyKey()).isNull();
        assertThat(second.getIdempotencyKey()).isNull();
    }

    /** Ids of a freshly created, bookable 100-seat tier and the user booking it. */
    private record Fixture(Long userId, Long eventId, Long tierId) {
    }

    private Fixture createFixture() {
        String unique = UUID.randomUUID().toString();

        User attendee = userRepository.save(User.builder()
                .firstName("Idem").lastName("Potent")
                .email("idem-" + unique + "@test.com")
                .passwordHash("irrelevant-not-checked-here")
                .role(Role.USER)
                .build());

        User organizer = userRepository.save(User.builder()
                .firstName("Idem").lastName("Organizer")
                .email("idem-organizer-" + unique + "@test.com")
                .passwordHash("irrelevant-not-checked-here")
                .role(Role.ORGANIZER)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Idempotency Category " + unique)
                .description("Fixture category for the booking idempotency test")
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Idempotency Venue")
                .address("1 Test St")
                .city("Test City")
                .country("Test Country")
                .totalCapacity(500)
                .build());

        Event event = eventRepository.save(Event.builder()
                .title("Idempotency Event " + unique)
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

        // Redis is warmed at startup, so a tier created afterwards needs its counter seeded.
        inventoryService.setAvailableCount(tier.getId(), tier.getAvailableCount());

        return new Fixture(attendee.getId(), event.getId(), tier.getId());
    }
}
