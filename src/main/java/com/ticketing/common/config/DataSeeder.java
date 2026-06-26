package com.ticketing.common.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
@Profile("local")
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

            // ── Categories (V9) ────────────────────────────────────────────────────────
            Category music    = categoryRepository.findByName("Music")
                    .orElseThrow(() -> new IllegalStateException("Category 'Music' missing"));
            Category sports   = categoryRepository.findByName("Sports")
                    .orElseThrow(() -> new IllegalStateException("Category 'Sports' missing"));
            Category comedy   = categoryRepository.findByName("Comedy")
                    .orElseThrow(() -> new IllegalStateException("Category 'Comedy' missing"));
            Category theater  = categoryRepository.findByName("Theater")
                    .orElseThrow(() -> new IllegalStateException("Category 'Theater' missing"));
            Category festival = categoryRepository.findByName("Festival")
                    .orElseThrow(() -> new IllegalStateException("Category 'Festival' missing"));

            // ── Venues (V9 existing) ───────────────────────────────────────────────────
            Venue cairoStadium   = venueRepository.findByName("Cairo International Stadium")
                    .orElseThrow(() -> new IllegalStateException("V9 venue missing: Cairo International Stadium"));
            Venue operaHouse     = venueRepository.findByName("Cairo Opera House")
                    .orElseThrow(() -> new IllegalStateException("V9 venue missing: Cairo Opera House"));
            Venue bibliotheca    = venueRepository.findByName("Bibliotheca Alexandrina")
                    .orElseThrow(() -> new IllegalStateException("V9 venue missing: Bibliotheca Alexandrina"));
            Venue gounaCenter    = venueRepository.findByName("El Gouna Conference and Culture Center")
                    .orElseThrow(() -> new IllegalStateException("V9 venue missing: El Gouna"));

            // ── Venues (V12 new cities) ───────────────────────────────────────────────
            Venue gem            = venueRepository.findByName("Grand Egyptian Museum")
                    .orElseThrow(() -> new IllegalStateException("V12 venue missing: Grand Egyptian Museum"));
            Venue karnak         = venueRepository.findByName("Karnak Temple Sound and Light Venue")
                    .orElseThrow(() -> new IllegalStateException("V12 venue missing: Karnak Temple"));
            Venue hurghadaMarina = venueRepository.findByName("Hurghada Marina Festival Grounds")
                    .orElseThrow(() -> new IllegalStateException("V12 venue missing: Hurghada Marina"));
            Venue philae         = venueRepository.findByName("Philae Temple Sound and Light Venue")
                    .orElseThrow(() -> new IllegalStateException("V12 venue missing: Philae Temple"));

            // ── Event 1: Amr Diab Concert — Cairo ─────────────────────────────────────
            Event concert = new Event();
            concert.setTitle("Amr Diab Live — Cairo Stadium 2026");
            concert.setDescription("An unforgettable night with the Habibi star Amr Diab at Cairo International Stadium.");
            concert.setOrganizer(organizer);
            concert.setCategory(music);
            concert.setVenue(cairoStadium);
            concert.setStartDate(cairoTime(15, 20, 0));
            concert.setEndDate(cairoTime(16, 0, 0));
            concert.setStatus(EventStatus.PUBLISHED);
            concert.setCoverImageUrl("https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=800&q=80");
            concert = eventRepository.save(concert);
            createTier(ticketTierRepository, concert, "Field — General", "600.00", 50000, null);
            createTier(ticketTierRepository, concert, "VIP Grandstand",  "2500.00",  3000, "Premium seating with complimentary refreshments.");

            // ── Event 2: Cairo Jazz Festival — Cairo ──────────────────────────────────
            Event jazzFest = new Event();
            jazzFest.setTitle("Cairo Jazz Festival 2026");
            jazzFest.setDescription("Three nights of world-class jazz featuring Egyptian and international artists on the banks of the Nile.");
            jazzFest.setOrganizer(organizer);
            jazzFest.setCategory(festival);
            jazzFest.setVenue(operaHouse);
            jazzFest.setStartDate(cairoTime(40, 19, 0));
            jazzFest.setEndDate(cairoTime(43, 23, 0));
            jazzFest.setStatus(EventStatus.PUBLISHED);
            jazzFest.setCoverImageUrl("https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=800&q=80");
            jazzFest = eventRepository.save(jazzFest);
            createTier(ticketTierRepository, jazzFest, "General Admission", "800.00",  1000, null);
            createTier(ticketTierRepository, jazzFest, "3-Night Pass",      "2000.00",  200, "Full access to all three festival nights.");

            // ── Event 3: El Gouna Tech Summit — Red Sea ───────────────────────────────
            Event techSummit = new Event();
            techSummit.setTitle("El Gouna Tech & Innovation Summit 2026");
            techSummit.setDescription("Egypt's premier technology and startup conference hosted at the beautiful Red Sea resort of El Gouna.");
            techSummit.setOrganizer(organizer);
            techSummit.setCategory(festival);
            techSummit.setVenue(gounaCenter);
            techSummit.setStartDate(cairoTime(60, 9, 0));
            techSummit.setEndDate(cairoTime(62, 18, 0));
            techSummit.setStatus(EventStatus.PUBLISHED);
            techSummit.setCoverImageUrl("https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&q=80");
            techSummit = eventRepository.save(techSummit);
            createTier(ticketTierRepository, techSummit, "Standard Pass",      "1500.00", 2500, null);
            createTier(ticketTierRepository, techSummit, "VIP Executive Pass", "4000.00",  200, "Includes speaker dinners, networking lounge, and resort day pass.");

            // ── Event 4: Alexandria Jazz Night — Alexandria ────────────────────────────
            Event alexJazz = new Event();
            alexJazz.setTitle("Alexandria Jazz on the Mediterranean 2026");
            alexJazz.setDescription("An intimate jazz evening at the iconic Bibliotheca Alexandrina overlooking the Mediterranean Sea.");
            alexJazz.setOrganizer(organizer);
            alexJazz.setCategory(music);
            alexJazz.setVenue(bibliotheca);
            alexJazz.setStartDate(cairoTime(22, 20, 30));
            alexJazz.setEndDate(cairoTime(22, 23, 30));
            alexJazz.setStatus(EventStatus.PUBLISHED);
            alexJazz.setCoverImageUrl("https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?w=800&q=80");
            alexJazz = eventRepository.save(alexJazz);
            createTier(ticketTierRepository, alexJazz, "General Admission", "750.00",  500, null);
            createTier(ticketTierRepository, alexJazz, "VIP Balcony",       "2200.00",  80, "Reserved balcony seating with sea view.");

            // ── Event 5: Karnak Heritage Night — Luxor ────────────────────────────────
            Event karnakShow = new Event();
            karnakShow.setTitle("Karnak Temple Heritage Night Show");
            karnakShow.setDescription("Experience 3,000 years of Egyptian history through a breathtaking sound and light show among the ancient columns of Karnak.");
            karnakShow.setOrganizer(organizer);
            karnakShow.setCategory(theater);
            karnakShow.setVenue(karnak);
            karnakShow.setStartDate(cairoTime(30, 20, 0));
            karnakShow.setEndDate(cairoTime(30, 22, 0));
            karnakShow.setStatus(EventStatus.PUBLISHED);
            karnakShow.setCoverImageUrl("https://images.unsplash.com/photo-1539650116574-8efeb43e2750?w=800&q=80");
            karnakShow = eventRepository.save(karnakShow);
            createTier(ticketTierRepository, karnakShow, "Standard Entry", "500.00",  2000, null);
            createTier(ticketTierRepository, karnakShow, "Premium Front",  "1200.00",  300, "Front-row reserved seating with souvenir guide.");

            // ── Event 6: Hurghada Beach Sports Festival ────────────────────────────────
            Event hurghadaSports = new Event();
            hurghadaSports.setTitle("Hurghada Beach Sports Festival 2026");
            hurghadaSports.setDescription("Three days of beach volleyball, water polo, and Red Sea sports competitions on the shores of Hurghada Marina.");
            hurghadaSports.setOrganizer(organizer);
            hurghadaSports.setCategory(sports);
            hurghadaSports.setVenue(hurghadaMarina);
            hurghadaSports.setStartDate(cairoTime(50, 10, 0));
            hurghadaSports.setEndDate(cairoTime(53, 20, 0));
            hurghadaSports.setStatus(EventStatus.PUBLISHED);
            hurghadaSports.setCoverImageUrl("https://images.unsplash.com/photo-1530549387789-4c1017266635?w=800&q=80");
            hurghadaSports = eventRepository.save(hurghadaSports);
            createTier(ticketTierRepository, hurghadaSports, "Day Pass",       "350.00",  3000, null);
            createTier(ticketTierRepository, hurghadaSports, "Full Festival Pass", "900.00", 500, "Access to all 3 days including beach party.");

            // ── Event 7: Cairo Comedy Gala ─────────────────────────────────────────────
            Event comedyGala = new Event();
            comedyGala.setTitle("Cairo Comedy Gala 2026");
            comedyGala.setDescription("Egypt's biggest stand-up comedy night featuring the country's top comedians on the historic Cairo Opera House stage.");
            comedyGala.setOrganizer(organizer);
            comedyGala.setCategory(comedy);
            comedyGala.setVenue(operaHouse);
            comedyGala.setStartDate(cairoTime(28, 20, 0));
            comedyGala.setEndDate(cairoTime(28, 23, 0));
            comedyGala.setStatus(EventStatus.PUBLISHED);
            comedyGala.setCoverImageUrl("https://images.unsplash.com/photo-1527224857830-43a7acc85260?w=800&q=80");
            comedyGala = eventRepository.save(comedyGala);
            createTier(ticketTierRepository, comedyGala, "Standard",  "400.00",  800, null);
            createTier(ticketTierRepository, comedyGala, "VIP Table",  "1500.00", 100, "Front-row table seating with dinner included.");

            // ── Event 8: Aswan Nile Festival of Lights — Aswan ────────────────────────
            Event aswanFest = new Event();
            aswanFest.setTitle("Aswan Nile Festival of Lights 2026");
            aswanFest.setDescription("A magical evening light festival at the ancient Philae Temple on Agilkia Island, with music, art, and Nile boat rides.");
            aswanFest.setOrganizer(organizer);
            aswanFest.setCategory(festival);
            aswanFest.setVenue(philae);
            aswanFest.setStartDate(cairoTime(70, 18, 0));
            aswanFest.setEndDate(cairoTime(72, 22, 0));
            aswanFest.setStatus(EventStatus.PUBLISHED);
            aswanFest.setCoverImageUrl("https://images.unsplash.com/photo-1568322445389-f64ac2515020?w=800&q=80");
            aswanFest = eventRepository.save(aswanFest);
            createTier(ticketTierRepository, aswanFest, "General Entry",        "650.00",  1500, null);
            createTier(ticketTierRepository, aswanFest, "Nile Cruise + Entry",  "1800.00",  250, "Includes private Nile boat ride to Philae at sunset.");

            // ── Event 9: Grand Egyptian Museum Gala — Giza ────────────────────────────
            Event gemGala = new Event();
            gemGala.setTitle("Grand Egyptian Museum: A Night Among the Pharaohs");
            gemGala.setDescription("An exclusive after-hours gala in the world's largest archaeological museum, including a private viewing of Tutankhamun's treasures.");
            gemGala.setOrganizer(organizer);
            gemGala.setCategory(music);
            gemGala.setVenue(gem);
            gemGala.setStartDate(cairoTime(10, 19, 0));
            gemGala.setEndDate(cairoTime(11, 0, 0));
            gemGala.setStatus(EventStatus.PUBLISHED);
            gemGala.setCoverImageUrl("https://images.unsplash.com/photo-1539768942893-daf53e448371?w=800&q=80");
            gemGala = eventRepository.save(gemGala);
            createTier(ticketTierRepository, gemGala, "Gala Entry",    "2500.00", 5000, null);
            createTier(ticketTierRepository, gemGala, "Platinum Pass", "6000.00",  300, "Private guided tour of Tutankhamun Gallery + champagne reception.");

            log.info("Seeding complete — 9 Egyptian events created across Cairo, Alexandria, Giza, Luxor, Hurghada, and Aswan.");
        };
    }

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private static Instant cairoTime(int daysFromNow, int hour, int minute) {
        return ZonedDateTime.of(
            LocalDate.now(CAIRO).plusDays(daysFromNow),
            LocalTime.of(hour, minute),
            CAIRO
        ).toInstant();
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
