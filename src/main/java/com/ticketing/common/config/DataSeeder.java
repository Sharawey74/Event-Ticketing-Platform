package com.ticketing.common.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ticketing.booking.model.TicketTier;
import com.ticketing.booking.repository.TicketTierRepository;
import com.ticketing.event.model.Category;
import com.ticketing.event.model.Event;
import com.ticketing.event.model.EventStatus;
import com.ticketing.event.model.Venue;
import com.ticketing.event.repository.CategoryRepository;
import com.ticketing.event.repository.EventRepository;
import com.ticketing.event.repository.VenueRepository;
import com.ticketing.user.model.Role;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;

/**
 * Seeds Egyptian demo events on first startup (local / default profiles only).
 * Looks up categories and venues by name — safe against auto-increment ID drift.
 */
@Configuration
@Profile({"local", "default"})
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    ApplicationRunner seedDemoData(
            UserRepository userRepository,
            EventRepository eventRepository,
            CategoryRepository categoryRepository,
            VenueRepository venueRepository,
            TicketTierRepository ticketTierRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            if (eventRepository.count() > 0) {
                log.info("Events already exist — skipping seed.");
                return;
            }

            log.info("Seeding Egyptian demo events...");

            // ── Organizer ──────────────────────────────────────────────────────────────
            User organizer = userRepository.findByEmail("organizer@eventora.com").orElseGet(() -> {
                User u = new User();
                u.setFirstName("Ahmed");
                u.setLastName("Hassan");
                u.setEmail("organizer@eventora.com");
                u.setPasswordHash(passwordEncoder.encode("Password123!"));
                u.setRole(Role.ORGANIZER);
                return userRepository.save(u);
            });

            // ── Reference data — look up by name (safe against ID drift) ──────────────
            Category music = categoryRepository.findByName("Music")
                    .orElseThrow(() -> new IllegalStateException("Category 'Music' missing — did V9 migration run?"));
            Category festival = categoryRepository.findByName("Festival")
                    .orElseThrow(() -> new IllegalStateException("Category 'Festival' missing — did V9 migration run?"));

            Venue cairoStadium = venueRepository.findByName("Cairo International Stadium")
                    .orElseThrow(() -> new IllegalStateException("Venue 'Cairo International Stadium' missing — did V9 migration run?"));
            Venue operaHouse = venueRepository.findByName("Cairo Opera House")
                    .orElseThrow(() -> new IllegalStateException("Venue 'Cairo Opera House' missing — did V9 migration run?"));
            Venue gounaCenter = venueRepository.findByName("El Gouna Conference and Culture Center")
                    .orElseThrow(() -> new IllegalStateException("Venue 'El Gouna Conference and Culture Center' missing — did V9 migration run?"));

            Instant now = Instant.now();

            // ── Event 1: Amr Diab Concert ──────────────────────────────────────────────
            Event concert = new Event();
            concert.setTitle("Amr Diab Live — Cairo Stadium 2026");
            concert.setDescription("An unforgettable night with the Habibi star Amr Diab at Cairo International Stadium.");
            concert.setOrganizer(organizer);
            concert.setCategory(music);
            concert.setVenue(cairoStadium);
            concert.setStartDate(now.plus(15, ChronoUnit.DAYS));
            concert.setEndDate(now.plus(15, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS));
            concert.setStatus(EventStatus.PUBLISHED);
            concert.setCoverImageUrl("https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=800&q=80");
            concert = eventRepository.save(concert);

            createTier(ticketTierRepository, concert, "Field — General",   "600.00",  50000, null);
            createTier(ticketTierRepository, concert, "VIP Grandstand",    "2500.00",  3000, "Premium seating with complimentary refreshments.");

            // ── Event 2: Cairo Jazz Festival ───────────────────────────────────────────
            Event jazzFest = new Event();
            jazzFest.setTitle("Cairo Jazz Festival 2026");
            jazzFest.setDescription("Three nights of world-class jazz featuring Egyptian and international artists on the banks of the Nile.");
            jazzFest.setOrganizer(organizer);
            jazzFest.setCategory(festival);
            jazzFest.setVenue(operaHouse);
            jazzFest.setStartDate(now.plus(40, ChronoUnit.DAYS));
            jazzFest.setEndDate(now.plus(43, ChronoUnit.DAYS));
            jazzFest.setStatus(EventStatus.PUBLISHED);
            jazzFest.setCoverImageUrl("https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=800&q=80");
            jazzFest = eventRepository.save(jazzFest);

            createTier(ticketTierRepository, jazzFest, "General Admission", "800.00",  1000, null);
            createTier(ticketTierRepository, jazzFest, "3-Night Pass",     "2000.00",   200, "Full access to all three festival nights.");

            // ── Event 3: El Gouna Tech Summit ─────────────────────────────────────────
            Event techSummit = new Event();
            techSummit.setTitle("El Gouna Tech & Innovation Summit 2026");
            techSummit.setDescription("Egypt's premier technology and startup conference hosted at the beautiful Red Sea resort of El Gouna.");
            techSummit.setOrganizer(organizer);
            techSummit.setCategory(festival);
            techSummit.setVenue(gounaCenter);
            techSummit.setStartDate(now.plus(60, ChronoUnit.DAYS));
            techSummit.setEndDate(now.plus(62, ChronoUnit.DAYS));
            techSummit.setStatus(EventStatus.PUBLISHED);
            techSummit.setCoverImageUrl("https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&q=80");
            techSummit = eventRepository.save(techSummit);

            createTier(ticketTierRepository, techSummit, "Standard Pass",       "1500.00", 2500, null);
            createTier(ticketTierRepository, techSummit, "VIP Executive Pass",  "4000.00",  200, "Includes speaker dinners, networking lounge, and resort day pass.");

            log.info("Seeding complete — 3 Egyptian events created.");
        };
    }

    private void createTier(TicketTierRepository repo, Event event,
                             String name, String price, int capacity, String description) {
        TicketTier tier = new TicketTier();
        tier.setEvent(event);
        tier.setTierName(name);
        tier.setBasePrice(new BigDecimal(price));
        tier.setTotalCapacity(capacity);
        tier.setAvailableCount(capacity);
        if (description != null) {
            tier.setDescription(description);
        }
        repo.save(tier);
    }
}
