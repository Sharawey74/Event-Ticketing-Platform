-- V13__seed_egypt_private_events.sql
-- 15 real-world-plausible, privately organized Egyptian events.
-- Excludes museums and government-run cultural/antiquities sites by design.
-- IMMUTABLE: Do not edit after first run. NEXT MIGRATION MUST BE V14__...

-- ─────────────────────────────────────────────────────────────────────────────
-- New category (existing 5 from V9 are reused untouched)
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO categories (name, description, icon_url) VALUES
  ('Conference', 'Business, tech, and industry conferences and summits', 'briefcase');

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed organizer accounts (role = ORGANIZER)
-- Password for both (seed/test only): EventoraSeed@2026
-- bcrypt hash below verified to round-trip with Spring Security BCryptPasswordEncoder
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO users (email, password_hash, first_name, last_name, role) VALUES
  ('events@caironightslive.eg',   '$2b$10$S0446NYGuL.wNoWUJmF0C.Lb9I/n4pa9kJbICirv1ZJzq1hdkvLXS', 'Cairo Nights', 'Live', 'ORGANIZER'),
  ('bookings@redsealiveent.eg',   '$2b$10$S0446NYGuL.wNoWUJmF0C.Lb9I/n4pa9kJbICirv1ZJzq1hdkvLXS', 'Red Sea Live', 'Entertainment', 'ORGANIZER');

-- ─────────────────────────────────────────────────────────────────────────────
-- 15 events. organizer/category/venue resolved by unique name/email —
-- never hardcode auto-generated IDs across migrations.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO events (title, description, organizer_id, category_id, venue_id, cover_image_url, start_date, end_date, sales_open_date, sales_close_date, status) VALUES

('Cairo Skyline Music Nights',
 'An open-air concert night under the Cairo skyline, featuring live regional acts.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Music'),
 (SELECT id FROM venues WHERE name = 'Cairo International Stadium' LIMIT 1),
 'https://picsum.photos/seed/cairo-skyline-music-nights/800/500',
 '2026-08-14T19:00:00+02:00', '2026-08-14T23:30:00+02:00', '2026-07-01T00:00:00+02:00', '2026-08-14T19:00:00+02:00', 'PUBLISHED'),

('Nile Comedy Jam',
 'A stand-up showcase bringing together Egypt''s sharpest comedic voices for one night in Alexandria.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Comedy'),
 (SELECT id FROM venues WHERE name = 'Sidi Gaber Conference Centre' LIMIT 1),
 'https://picsum.photos/seed/nile-comedy-jam/800/500',
 '2026-08-21T20:00:00+02:00', '2026-08-21T22:30:00+02:00', '2026-07-01T00:00:00+02:00', '2026-08-21T20:00:00+02:00', 'PUBLISHED'),

('Alexandria Stage Theater: The Last Felucca',
 'An original stage play set along the Alexandria corniche, exploring family and change.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Theater'),
 (SELECT id FROM venues WHERE name = 'Sidi Gaber Conference Centre' LIMIT 1),
 'https://picsum.photos/seed/last-felucca/800/500',
 '2026-09-04T19:30:00+02:00', '2026-09-04T21:30:00+02:00', '2026-07-01T00:00:00+02:00', '2026-09-04T19:30:00+02:00', 'PUBLISHED'),

('Red Sea Beats Festival',
 'Two days of electronic and live music on the Hurghada marina, with multiple stages.',
 (SELECT id FROM users WHERE email = 'bookings@redsealiveent.eg'),
 (SELECT id FROM categories WHERE name = 'Festival'),
 (SELECT id FROM venues WHERE name = 'Hurghada Marina Festival Grounds' LIMIT 1),
 'https://picsum.photos/seed/red-sea-beats-festival/800/500',
 '2026-09-11T16:00:00+02:00', '2026-09-12T23:59:00+02:00', '2026-07-01T00:00:00+02:00', '2026-09-11T16:00:00+02:00', 'PUBLISHED'),

('El Gouna Film & Music Nights',
 'A three-day open-air cultural program in El Gouna combining film screenings and live music.',
 (SELECT id FROM users WHERE email = 'bookings@redsealiveent.eg'),
 (SELECT id FROM categories WHERE name = 'Festival'),
 (SELECT id FROM venues WHERE name = 'El Gouna Conference and Culture Center' LIMIT 1),
 'https://picsum.photos/seed/el-gouna-film-music/800/500',
 '2026-09-25T17:00:00+02:00', '2026-09-27T23:59:00+02:00', '2026-07-01T00:00:00+02:00', '2026-09-25T17:00:00+02:00', 'PUBLISHED'),

('RiseUp Business Summit Cairo',
 'A two-day summit for founders, investors, and operators across Egypt''s private sector.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Conference'),
 (SELECT id FROM venues WHERE name = 'Cairo International Stadium' LIMIT 1),
 'https://picsum.photos/seed/riseup-business-summit/800/500',
 '2026-10-06T09:00:00+02:00', '2026-10-07T18:00:00+02:00', '2026-07-01T00:00:00+02:00', '2026-10-06T09:00:00+02:00', 'PUBLISHED'),

('Alexandria Padel Masters',
 'A three-day amateur-to-pro padel tournament with courtside and general viewing.',
 (SELECT id FROM users WHERE email = 'bookings@redsealiveent.eg'),
 (SELECT id FROM categories WHERE name = 'Sports'),
 (SELECT id FROM venues WHERE name = 'Borg El Arab Stadium' LIMIT 1),
 'https://picsum.photos/seed/alexandria-padel-masters/800/500',
 '2026-10-16T09:00:00+02:00', '2026-10-18T20:00:00+02:00', '2026-07-01T00:00:00+02:00', '2026-10-16T09:00:00+02:00', 'PUBLISHED'),

('Dahab Desert Comedy Night',
 'An intimate stand-up night against the backdrop of Dahab''s bay.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Comedy'),
 (SELECT id FROM venues WHERE name = 'Dahab Conference and Arts Centre' LIMIT 1),
 'https://picsum.photos/seed/dahab-desert-comedy/800/500',
 '2026-10-23T20:00:00+02:00', '2026-10-23T22:30:00+02:00', '2026-07-01T00:00:00+02:00', '2026-10-23T20:00:00+02:00', 'PUBLISHED'),

('Sharm Sunset Music Festival',
 'A sunset-to-night live music festival on the Naama Bay promenade.',
 (SELECT id FROM users WHERE email = 'bookings@redsealiveent.eg'),
 (SELECT id FROM categories WHERE name = 'Music'),
 (SELECT id FROM venues WHERE name = 'Naama Bay Open Stage' LIMIT 1),
 'https://picsum.photos/seed/sharm-sunset-music/800/500',
 '2026-11-06T17:00:00+02:00', '2026-11-06T23:59:00+02:00', '2026-07-01T00:00:00+02:00', '2026-11-06T17:00:00+02:00', 'PUBLISHED'),

('Hurghada Marina Food & Craft Fair',
 'A two-day marina-side fair featuring local food vendors and craft makers.',
 (SELECT id FROM users WHERE email = 'bookings@redsealiveent.eg'),
 (SELECT id FROM categories WHERE name = 'Festival'),
 (SELECT id FROM venues WHERE name = 'Hurghada Marina Festival Grounds' LIMIT 1),
 'https://picsum.photos/seed/hurghada-food-craft-fair/800/500',
 '2026-11-13T12:00:00+02:00', '2026-11-14T22:00:00+02:00', '2026-07-01T00:00:00+02:00', '2026-11-13T12:00:00+02:00', 'PUBLISHED'),

('Steigenberger Jazz & Blues Evening',
 'A seated jazz and blues evening at the Steigenberger Al Dau Club Arena.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Music'),
 (SELECT id FROM venues WHERE name = 'Steigenberger Al Dau Club Arena' LIMIT 1),
 'https://picsum.photos/seed/steigenberger-jazz-blues/800/500',
 '2026-11-20T20:00:00+02:00', '2026-11-20T23:00:00+02:00', '2026-07-01T00:00:00+02:00', '2026-11-20T20:00:00+02:00', 'PUBLISHED'),

('Cairo Fintech & Startups Forum',
 'A two-day forum on fintech, payments, and startup growth in the Egyptian market.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Conference'),
 (SELECT id FROM venues WHERE name = 'Cairo International Stadium' LIMIT 1),
 'https://picsum.photos/seed/cairo-fintech-forum/800/500',
 '2026-12-02T09:00:00+02:00', '2026-12-03T17:00:00+02:00', '2026-07-01T00:00:00+02:00', '2026-12-02T09:00:00+02:00', 'PUBLISHED'),

('Dahab Bay Stage Play: Sea of Sinai',
 'A original one-act play staged at the Dahab Conference and Arts Centre.',
 (SELECT id FROM users WHERE email = 'bookings@redsealiveent.eg'),
 (SELECT id FROM categories WHERE name = 'Theater'),
 (SELECT id FROM venues WHERE name = 'Dahab Conference and Arts Centre' LIMIT 1),
 'https://picsum.photos/seed/sea-of-sinai/800/500',
 '2026-12-11T19:30:00+02:00', '2026-12-11T21:30:00+02:00', '2026-07-01T00:00:00+02:00', '2026-12-11T19:30:00+02:00', 'PUBLISHED'),

('Red Sea Triathlon Awards Night',
 'The awards and celebration evening following the Red Sea Triathlon series.',
 (SELECT id FROM users WHERE email = 'bookings@redsealiveent.eg'),
 (SELECT id FROM categories WHERE name = 'Sports'),
 (SELECT id FROM venues WHERE name = 'Hurghada Marina Festival Grounds' LIMIT 1),
 'https://picsum.photos/seed/red-sea-triathlon-awards/800/500',
 '2027-01-15T19:00:00+02:00', '2027-01-15T22:00:00+02:00', '2026-07-01T00:00:00+02:00', '2027-01-15T19:00:00+02:00', 'PUBLISHED'),

('Sharm International Comedy Nights',
 'An international stand-up lineup performing on the Naama Bay promenade stage.',
 (SELECT id FROM users WHERE email = 'events@caironightslive.eg'),
 (SELECT id FROM categories WHERE name = 'Comedy'),
 (SELECT id FROM venues WHERE name = 'Naama Bay Open Stage' LIMIT 1),
 'https://picsum.photos/seed/sharm-comedy-nights/800/500',
 '2027-02-05T20:00:00+02:00', '2027-02-05T22:30:00+02:00', '2026-07-01T00:00:00+02:00', '2027-02-05T20:00:00+02:00', 'PUBLISHED');

-- ─────────────────────────────────────────────────────────────────────────────
-- Ticket tiers — 2 per event (General/Day-pass + VIP/Premium tier).
-- available_count = total_capacity at seed time (nothing sold yet).
-- Resolved by event title — titles above are guaranteed unique within this migration.
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO ticket_tiers (event_id, tier_name, base_price, total_capacity, available_count) VALUES
((SELECT id FROM events WHERE title = 'Cairo Skyline Music Nights'),          'General', 500.00, 4000, 4000),
((SELECT id FROM events WHERE title = 'Cairo Skyline Music Nights'),          'VIP',     1200.00, 500, 500),

((SELECT id FROM events WHERE title = 'Nile Comedy Jam'),                     'General', 300.00, 800, 800),
((SELECT id FROM events WHERE title = 'Nile Comedy Jam'),                     'VIP',     600.00, 150, 150),

((SELECT id FROM events WHERE title = 'Alexandria Stage Theater: The Last Felucca'), 'General', 250.00, 600, 600),
((SELECT id FROM events WHERE title = 'Alexandria Stage Theater: The Last Felucca'), 'VIP',     500.00, 100, 100),

((SELECT id FROM events WHERE title = 'Red Sea Beats Festival'),              'General', 700.00, 3000, 3000),
((SELECT id FROM events WHERE title = 'Red Sea Beats Festival'),              'VIP',     1500.00, 400, 400),

((SELECT id FROM events WHERE title = 'El Gouna Film & Music Nights'),        'General', 600.00, 1500, 1500),
((SELECT id FROM events WHERE title = 'El Gouna Film & Music Nights'),        'VIP',     1400.00, 200, 200),

((SELECT id FROM events WHERE title = 'RiseUp Business Summit Cairo'),        'Standard Pass', 2500.00, 1200, 1200),
((SELECT id FROM events WHERE title = 'RiseUp Business Summit Cairo'),        'All-Access Pass', 4500.00, 200, 200),

((SELECT id FROM events WHERE title = 'Alexandria Padel Masters'),           'General', 200.00, 3000, 3000),
((SELECT id FROM events WHERE title = 'Alexandria Padel Masters'),           'Courtside VIP', 450.00, 300, 300),

((SELECT id FROM events WHERE title = 'Dahab Desert Comedy Night'),          'General', 280.00, 500, 500),
((SELECT id FROM events WHERE title = 'Dahab Desert Comedy Night'),          'VIP',     550.00, 80, 80),

((SELECT id FROM events WHERE title = 'Sharm Sunset Music Festival'),        'General', 450.00, 2500, 2500),
((SELECT id FROM events WHERE title = 'Sharm Sunset Music Festival'),        'VIP',     950.00, 350, 350),

((SELECT id FROM events WHERE title = 'Hurghada Marina Food & Craft Fair'),  'Day Pass', 250.00, 3500, 3500),
((SELECT id FROM events WHERE title = 'Hurghada Marina Food & Craft Fair'),  'Weekend Pass', 400.00, 1000, 1000),

((SELECT id FROM events WHERE title = 'Steigenberger Jazz & Blues Evening'), 'General', 400.00, 1200, 1200),
((SELECT id FROM events WHERE title = 'Steigenberger Jazz & Blues Evening'), 'VIP Table', 900.00, 150, 150),

((SELECT id FROM events WHERE title = 'Cairo Fintech & Startups Forum'),     'Standard Pass', 2000.00, 1000, 1000),
((SELECT id FROM events WHERE title = 'Cairo Fintech & Startups Forum'),     'Investor Pass', 4000.00, 150, 150),

((SELECT id FROM events WHERE title = 'Dahab Bay Stage Play: Sea of Sinai'), 'General', 220.00, 500, 500),
((SELECT id FROM events WHERE title = 'Dahab Bay Stage Play: Sea of Sinai'), 'VIP',     450.00, 80, 80),

((SELECT id FROM events WHERE title = 'Red Sea Triathlon Awards Night'),     'General', 300.00, 1500, 1500),
((SELECT id FROM events WHERE title = 'Red Sea Triathlon Awards Night'),     'VIP',     650.00, 200, 200),

((SELECT id FROM events WHERE title = 'Sharm International Comedy Nights'), 'General', 350.00, 2000, 2000),
((SELECT id FROM events WHERE title = 'Sharm International Comedy Nights'), 'VIP',     700.00, 250, 250);
