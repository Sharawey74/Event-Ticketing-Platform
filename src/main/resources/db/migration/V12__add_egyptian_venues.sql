-- V12__add_egyptian_venues.sql
-- Adds real Egyptian tourism-city venues across Giza, Alexandria, Luxor,
-- Aswan, Hurghada, Sharm El Sheikh, and Dahab.
-- IMMUTABLE: Do not edit after first run. NEXT MIGRATION MUST BE V13__...

INSERT INTO venues (name, address, city, country, total_capacity) VALUES
  -- Giza
  ('Grand Egyptian Museum', 'Giza Plateau Road, Al Remaya Square', 'Giza', 'EG', 15000),
  ('Giza Pyramids Sound and Light Theatre', 'Pyramids Road, Giza Plateau', 'Giza', 'EG', 5000),

  -- Alexandria (supplements Bibliotheca Alexandrina and Borg El Arab already in V9)
  ('Alexandria Opera House', 'Al Horreya Road, El Mansheya', 'Alexandria', 'EG', 3500),
  ('Sidi Gaber Conference Centre', 'Sidi Gaber Station District', 'Alexandria', 'EG', 2500),

  -- Luxor
  ('Karnak Temple Sound and Light Venue', 'Karnak Temple Complex, East Bank', 'Luxor', 'EG', 4000),
  ('Luxor Cultural Palace', 'Khalid Ibn Al Walid Street, Luxor', 'Luxor', 'EG', 2000),

  -- Aswan
  ('Philae Temple Sound and Light Venue', 'Agilkia Island, Lake Nasser', 'Aswan', 'EG', 3000),
  ('Aswan Cultural Palace', 'Corniche El Nil, Aswan City', 'Aswan', 'EG', 1500),

  -- Hurghada
  ('Hurghada Marina Festival Grounds', 'El Mamsha Promenade, Hurghada', 'Hurghada', 'EG', 8000),
  ('Steigenberger Al Dau Club Arena', 'El Corniche Road, Hurghada', 'Hurghada', 'EG', 3000),

  -- Sharm El Sheikh
  ('Sharm El Sheikh International Convention Centre', 'Naama Bay, South Sinai', 'Sharm El Sheikh', 'EG', 5000),
  ('Naama Bay Open Stage', 'Naama Bay Promenade, South Sinai', 'Sharm El Sheikh', 'EG', 6000),

  -- Dahab
  ('Dahab Conference and Arts Centre', 'Mashraba District, South Sinai', 'Dahab', 'EG', 1000);
