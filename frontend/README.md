# Event Ticketing Frontend (Day 4)

This is the Next.js frontend for the Event Ticketing Platform Phase 1A.

## Requirements

- Node.js 18+
- npm
- Backend API running and reachable

## Environment Variables

Create `frontend/.env.local`:

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080
```

All API calls use `NEXT_PUBLIC_API_URL` via the shared Axios client in `src/lib/api.ts`.

## Commands

Run development server:

```bash
npm run dev
```

Build for production:

```bash
npm run build
```

Start production server locally:

```bash
npm run start
```

## Implemented Day 4 Foundation

- App Router + TypeScript strict mode
- React Query provider and query client
- Axios API client with JWT interceptor support
- Zustand auth store
- Home page with:
	- Hero search inputs (query/city/date)
	- Category filter pills
	- Upcoming events grid using real backend APIs
- Dedicated search page (`/search`) with URL/filter-driven results
- Navbar search box wired to `/search?q=` navigation

## Key Files

- `src/app/layout.tsx`
- `src/app/page.tsx`
- `src/lib/api.ts`
- `src/lib/events.ts`
- `src/lib/catalog.ts`
- `src/components/providers/app-providers.tsx`
