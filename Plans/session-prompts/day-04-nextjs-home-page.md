# Day 4 — Session Prompt
**Date:** Tuesday, April 7, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `intsructions.txt` content, send this as your next message:

```
We are on Day 4 — Next.js Frontend Initialization + Home Page.
Feature: nextjs-home-page

Active fixes today:
- No overlay fixes specifically for Day 4.
- Cross-cutting: API_URL must come from NEXT_PUBLIC_API_URL env var (never hardcoded).

Pre-conditions confirmed:
- Day 3 complete: all backend services running ✅
- GET /api/events returns paginated data ✅
- GET /api/search/events?q= returns filtered data ✅
- Docker Desktop is running ✅

This is a FRONTEND day (Next.js/TypeScript). No Java TDD today.
nextjs.instructions.md auto-injects for all .tsx files — no manual attachment needed.
React Query handles server state. Zustand handles client state.

Non-negotiable rules:
- NEXT_PUBLIC_API_URL env var for all API calls (never hardcode Railway URL)
- Axios instance in lib/api.ts with base URL + JWT interceptor
- All fetches via React Query (useQuery, useMutation) — no raw fetch()
- TypeScript strict mode: no 'any' types

Start with: scaffold the Next.js 14 project with npx create-next-app@latest.
Confirm the project compiles (npm run build) before implementing any page.
```

---

## Context Briefing

**What we're building today:**
Day 4 switches from backend to frontend. We initialize the Next.js 14 app and build the home page with real data from the Spring Boot API. This is the only frontend-focused day this week — the home page must render real event data by end of day.

**Why this matters:**
The frontend state management choices made today (React Query for server state, Zustand for client state) will be used by Day 13's Event Detail + Cart pages. Don't improvise different state libraries mid-project.

**Pre-conditions from Day 3:**
- Category/Venue CRUD working ✅
- GET /api/search/events returns paginated results ✅
- V9 seed data loaded (5 categories, 3 venues) ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 4
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## No New Fixes Today
No backend fixes today. The backend standards from Days 1-3 continue to apply to any backend code touched.

---

## Tasks (In Order)

### Morning (1 hr) — Architecture Planning
- Sketch 7 frontend pages and their components/API calls
- Document in `frontend/ARCHITECTURE.md`

### Afternoon (3.5 hrs) — Next.js Setup

#### Project Initialization
```bash
npx create-next-app@latest frontend --typescript --tailwind --eslint --app --src-dir
```

Install additional packages:
```bash
cd frontend
npm install @tanstack/react-query axios zustand react-hook-form zod @stripe/stripe-js @stripe/react-stripe-js lucide-react date-fns
```

#### API Client
- `src/lib/api.ts` — Axios base config with JWT interceptor (reads from Zustand auth store)
- `src/lib/queryClient.ts` — React Query client config
- `src/store/authStore.ts` — Zustand store for user + token state

#### Layout Components
- `Navbar.tsx`: logo, search bar, auth links, cart icon with badge
- `Footer.tsx` and `RootLayout.tsx`

#### Home Page (`app/page.tsx`)
- Hero section: gradient banner, headline, search input with city + date filters, CTA button
- Category filter row: horizontal scrollable pills (Music, Sports, Comedy, Theater, Festival)
- "Upcoming Events" grid: fetches `GET /api/events?status=PUBLISHED` with React Query (5-minute stale time)
- `EventCard.tsx`: gradient placeholder, event title, date badge, location, price, "Book Now" CTA

### Evening (1 hr) — Wire Search + Git
- Category pill click triggers new API call with filter params
- Git commit: `feat: add nextjs frontend with home page and event listing`

---

## Expected Deliverable / Success Criteria

```
[ ] Next.js app running on port 3000
[ ] Home page renders (no blank screen, no hydration errors)
[ ] Real event data fetched from Spring Boot (not mocked)
[ ] Category filter pills trigger filtered API call
[ ] EventCard displays event title, date, city, price
[ ] Zustand auth store created (for JWT storage)
[ ] React Query configured globally
```

---

## Skills to Attach This Session
- *(No backend skills needed today)*
- Attach `nextjs.instructions.md` from `.github/instructions/` is auto-injected for `.tsx` files
