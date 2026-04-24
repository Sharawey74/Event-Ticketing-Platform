# Frontend Architecture - Day 4

## Scope
Initialize the Next.js frontend and deliver a production-ready home page that consumes real backend event data.

## Page Plan (7 pages)
1. Home (`/`)
- Hero search (query, city, date)
- Category pills
- Upcoming events grid
- API: `GET /api/events`, `GET /api/search/events`, `GET /api/categories`, `GET /api/venues`

2. Event Detail (`/events/[id]`)
- Event overview and ticket tiers
- API: `GET /api/events/{id}`, ticket tier endpoint (Day 13)

3. Search Results (`/search`)
- Filtered list view
- API: `GET /api/search/events`

4. Auth (`/auth`)
- Login/register forms
- API: `POST /api/auth/login`, `POST /api/auth/register`

5. Cart/Checkout (`/checkout`)
- Booking summary and payment handoff
- API: booking and payment endpoints (Day 13/9)

6. User Dashboard (`/dashboard`)
- Booking history and tickets
- API: booking/ticket endpoints (Day 14)

7. Organizer Dashboard (`/organizer`)
- Event management and analytics cards
- API: organizer event endpoints (Day 15)

## State Management
- Server state: React Query
- Client state: Zustand (`authStore` now, cart slice later)

## API Layer
- Single Axios client in `src/lib/api.ts`
- Base URL must come from `NEXT_PUBLIC_API_URL`
- JWT token attached via request interceptor from Zustand auth store

## Component Plan for Day 4
- Layout: `Navbar`, `Footer`, root `AppProviders`
- Home widgets: `CategoryPills`, `EventCard`

## Data Contracts
- API envelope: `ApiResponse<T>`
- Paginated data: `PageResponse<T>`
- Domain DTOs used today: `EventResponse`, `CategoryResponse`, `VenueResponse`
