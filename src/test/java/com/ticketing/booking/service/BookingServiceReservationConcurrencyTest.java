package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ticketing.booking.model.Booking;
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

/**
 * Concurrency & Scalability Hardening (Day 21): extends the Fix 16.1 proof to the FULL
 * reservation path. InventoryServiceConcurrencyTest proves the Redis Lua floor guard is atomic
 * in isolation, but it never touches BookingService.reserveTickets() — so the D19-1 DB-side
 * TicketTier.availableCount decrement and Booking/Ticket row creation were never exercised
 * under the same 100-thread contention. Each thread uses a DISTINCT user, so lock keys
 * ("tier:X:user:Y") never collide across threads — contention is entirely on the shared Redis
 * inventory counter and the shared TicketTier DB row, not on DistributedLockService.
 *
 * This test originally caught a real bug: TicketTier.availableCount was mirrored to the DB via
 * a JPA read-modify-write (tier.setAvailableCount()+save()), which raced on the entity's
 * @Version whenever two different users' concurrent reservations touched the same tier row —
 * the loser's ObjectOptimisticLockingFailureException rolled back its DB write, but the earlier
 * Redis reserveSeat() decrement (not part of that JPA transaction) was never rolled back with
 * it, permanently leaking that seat. Fixed by replacing the read-modify-write with a single
 * atomic conditional UPDATE (TicketTierRepository.decrementAvailableCount()), which has no
 * read-then-write gap for concurrent writers to race on — this test's zero-uncaught-exceptions /
 * Redis-DB-consistency assertions are the regression guard for that fix.
 */
@SpringBootTest
@Testcontainers
@Import(TestcontainersConfiguration.class)
class BookingServiceReservationConcurrencyTest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TicketTierRepository ticketTierRepository;
    @Autowired
    private InventoryService inventoryService;

    @Test
    @DisplayName("reserveTickets end-to-end: 100 concurrent users / 50 seats -> exactly 50 bookings, "
            + "Redis and DB availableCount agree, no uncaught exceptions")
    void reserveTickets_whenConcurrentDifferentUsers_shouldNeverOversellAndKeepCountersConsistent() throws InterruptedException {
        int seatCount = 50;
        int threadCount = 100;

        User organizer = userRepository.save(User.builder()
                .firstName("Concurrency").lastName("Organizer")
                .email("concurrency-organizer-" + UUID.randomUUID() + "@test.com")
                .passwordHash("irrelevant-not-checked-for-token-auth")
                .role(Role.ORGANIZER)
                .build());

        Category category = categoryRepository.save(Category.builder()
                .name("Concurrency Category " + UUID.randomUUID())
                .description("Fixture category for the end-to-end reservation concurrency test")
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("Concurrency Venue")
                .address("1 Test St")
                .city("Test City")
                .country("Test Country")
                .totalCapacity(1000)
                .build());

        Event event = eventRepository.save(Event.builder()
                .title("Concurrency Load Event")
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
                .basePrice(BigDecimal.valueOf(10))
                .totalCapacity(seatCount)
                .availableCount(seatCount)
                .maxPerBooking(10)
                .build());

        inventoryService.setAvailableCount(tier.getId(), seatCount);

        List<Long> attendeeIds = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            User attendee = userRepository.save(User.builder()
                    .firstName("Attendee").lastName(String.valueOf(i))
                    .email("concurrency-attendee-" + i + "-" + UUID.randomUUID() + "@test.com")
                    .passwordHash("irrelevant-not-checked-for-token-auth")
                    .role(Role.USER)
                    .build());
            attendeeIds.add(attendee.getId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger expectedFailureCount = new AtomicInteger(0);
        AtomicInteger uncaughtCount = new AtomicInteger(0);
        List<Long> createdBookingIds = new CopyOnWriteArrayList<>();

        Long eventId = event.getId();
        Long tierId = tier.getId();

        for (Long userId : attendeeIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    try {
                        Booking booking = bookingService.reserveTickets(userId, eventId, tierId, 1);
                        createdBookingIds.add(booking.getId());
                        successCount.incrementAndGet();
                    } catch (IllegalStateException expected) {
                        // "Not enough tickets available" once the tier is exhausted — the
                        // expected outcome for the 50 threads that lose the race.
                        expectedFailureCount.incrementAndGet();
                    }
                } catch (Exception unexpected) {
                    uncaughtCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("all 100 threads finished within 60s").isTrue();
        assertThat(uncaughtCount.get()).as("no exception type escaped uncaught").isZero();
        assertThat(successCount.get()).isEqualTo(seatCount);
        assertThat(expectedFailureCount.get()).isEqualTo(threadCount - seatCount);
        assertThat(createdBookingIds).hasSize(seatCount);
        assertThat(createdBookingIds).doesNotHaveDuplicates();

        int redisRemaining = inventoryService.getAvailableCount(tierId);
        TicketTier persistedTier = ticketTierRepository.findById(tierId).orElseThrow();

        assertThat(redisRemaining).isZero();
        assertThat(persistedTier.getAvailableCount())
                .as("Fix D19-1: DB-persisted availableCount must stay numerically consistent with Redis under contention")
                .isEqualTo(redisRemaining);
    }
}
