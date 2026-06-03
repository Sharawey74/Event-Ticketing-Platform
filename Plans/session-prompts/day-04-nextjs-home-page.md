# Day 4 — Session Prompt
**Date:** Tuesday, April 7, 2026 | **Planned Hours:** 5 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

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
- DESIGN SYSTEM: All pages must use the Kinetic Premier design system (see below)

KINETIC PREMIER DESIGN SYSTEM (non-negotiable — applies to every component):
- Font: Inter (Google Fonts) — only font allowed.
- Primary: #630ed4 | Secondary: #4b41e1
- Button gradient: bg-gradient-to-r from-[#630ed4] to-[#4b41e1]
- Nav: fixed, h-20, bg-surface/80 + backdrop-blur-md, shadow-md
- Cards: rounded-xl shadow-md hover:shadow-xl, bg-surface-container-lowest (#ffffff)
- Inputs: rounded-lg border-outline-variant, focus:ring-primary
- Category pills: rounded-full
- Footer: bg-inverse-surface (#2d3133), text-surface-variant
- Spacing: px-edge-padding (24px), py-section-gap (80px between sections)
- Container: max-w-container-max (1280px) mx-auto

Start with: scaffold the Next.js 14 project with npx create-next-app@latest.
Confirm the project compiles (npm run build) before implementing any page.
```

---

## Context Briefing

**What we're building today:**
Day 4 switches from backend to frontend. We initialize the Next.js 14 app and build the **Home Page** with real data from the Spring Boot API. This is the only frontend-focused day this week — the home page must render real event data by end of day.

**Why this matters:**
The frontend state management choices made today (React Query for server state, Zustand for client state) will be used by Day 13's Event Detail + Checkout pages. Don't improvise different state libraries mid-project.

**Pre-conditions from Day 3:**
- Category/Venue CRUD working ✅
- GET /api/search/events returns paginated results ✅
- V9 seed data loaded (5 categories, 3 venues) ✅

---

## Active Plan Reference
- **Plan section:** Section 2 — Week 1, Day 4
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Tasks (In Order)

### Morning (1 hr) — Architecture Planning
- Sketch all 10 frontend pages and their components/API calls
- Document in `frontend/ARCHITECTURE.md`

**The 10 UI Pages to implement across Days 4, 13, 14, 15:**
1. **Home** (`/`) — Day 4
2. **Event Detail** (`/events/[id]`) — Day 13
3. **Secure Checkout** (`/checkout`) — Day 13 (Stripe redirect, no dedicated page needed; handled by Stripe-hosted page)
4. **Cart / Order Preview** (handled within Event Detail sticky panel — Day 13)
5. **Booking Confirmation** (`/bookings/[id]/confirmation`) — Day 13
6. **Login** (`/auth/login`) — Day 15
7. **Register** (`/auth/register`) — Day 15
8. **User Dashboard** (`/dashboard/bookings`) — Day 14
9. **Organizer Dashboard** (`/organizer/events`) — Day 15
10. **Create Event Form** (`/organizer/events/new`) — Day 15

### Afternoon (3.5 hrs) — Next.js Setup

#### Project Initialization
```bash
npx create-next-app@latest frontend --typescript --tailwind --eslint --app --src-dir
```

Install additional packages:
```bash
cd frontend
npm install @tanstack/react-query axios zustand react-hook-form zod @stripe/stripe-js @stripe/react-stripe-js date-fns qrcode.react
```

#### Tailwind Design System (`tailwind.config.ts`)

Extend Tailwind with the Kinetic Premier token set — this config must be added BEFORE any component work:

```typescript
// tailwind.config.ts
const config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        'surface': '#f7f9fb',
        'surface-dim': '#d8dadc',
        'surface-container': '#eceef0',
        'surface-container-low': '#f2f4f6',
        'surface-container-high': '#e6e8ea',
        'surface-container-highest': '#e0e3e5',
        'surface-container-lowest': '#ffffff',
        'on-surface': '#191c1e',
        'on-surface-variant': '#4a4455',
        'inverse-surface': '#2d3133',
        'inverse-on-surface': '#eff1f3',
        'outline': '#7b7487',
        'outline-variant': '#ccc3d8',
        'primary': '#630ed4',
        'on-primary': '#ffffff',
        'primary-container': '#7c3aed',
        'primary-fixed': '#eaddff',
        'primary-fixed-dim': '#d2bbff',
        'on-primary-fixed': '#25005a',
        'inverse-primary': '#d2bbff',
        'secondary': '#4b41e1',
        'on-secondary': '#ffffff',
        'secondary-fixed': '#e2dfff',
        'on-secondary-fixed': '#0f0069',
        'tertiary': '#474e64',
        'on-tertiary': '#ffffff',
        'tertiary-fixed': '#dae2fd',
        'on-tertiary-fixed': '#131b2e',
        'error': '#ba1a1a',
        'on-error': '#ffffff',
        'error-container': '#ffdad6',
        'on-error-container': '#93000a',
        'surface-tint': '#732ee4',
        'surface-variant': '#e0e3e5',
        'surface-bright': '#f7f9fb',
        'background': '#f7f9fb',
        'on-background': '#191c1e',
        'on-primary-container': '#ede0ff',
        'on-secondary-container': '#fffbff',
        'secondary-container': '#645efb',
        'tertiary-container': '#5e667d',
        'on-tertiary-container': '#dee5ff',
        'on-primary-fixed-variant': '#5a00c6',
        'on-secondary-fixed-variant': '#3323cc',
        'on-tertiary-fixed-variant': '#3f465c',
        'secondary-fixed-dim': '#c3c0ff',
        'tertiary-fixed-dim': '#bec6e0',
        'surface-dim': '#d8dadc',
      },
      spacing: {
        'edge-padding': '24px',
        'stack-lg': '32px',
        'stack-md': '16px',
        'stack-sm': '8px',
        'container-max': '1280px',
        'section-gap': '80px',
        'gutter': '24px',
      },
      borderRadius: {
        'DEFAULT': '0.25rem',
        'lg': '0.5rem',
        'xl': '0.75rem',
        'full': '9999px',
      },
      fontFamily: {
        body: ['Inter', 'sans-serif'],
        sans: ['Inter', 'sans-serif'],
      },
      fontSize: {
        'body': ['16px', { lineHeight: '24px', fontWeight: '400' }],
        'caption': ['12px', { lineHeight: '16px', fontWeight: '400' }],
        'hero-headline-mobile': ['36px', { lineHeight: '1.2', letterSpacing: '-0.02em', fontWeight: '700' }],
        'section-heading': ['24px', { lineHeight: '32px', letterSpacing: '-0.01em', fontWeight: '700' }],
        'hero-headline': ['56px', { lineHeight: '1.1', letterSpacing: '-0.02em', fontWeight: '700' }],
        'body-lg': ['18px', { lineHeight: '28px', fontWeight: '400' }],
        'label-sm': ['14px', { lineHeight: '20px', letterSpacing: '0.05em', fontWeight: '600' }],
      },
    },
  },
};
export default config;
```

Add Inter font to `src/app/layout.tsx`:
```typescript
import { Inter } from 'next/font/google';
const inter = Inter({ subsets: ['latin'] });
```

#### Shared Components (`src/components/`)

**`Navbar.tsx`** — matches Image 2 / Image 6 nav:
- Fixed, h-20, `bg-surface/80 backdrop-blur-md shadow-md border-b border-outline-variant`
- Brand: `VividPass` in `text-section-heading font-bold text-primary tracking-tighter`
- Desktop nav links: Discover, Schedule, Venues (text-on-surface-variant, hover:text-primary)
- Active link: `font-bold border-b-2 border-primary`
- Cart icon: Material Symbols `shopping_cart` (use Google Fonts Material Symbols Outlined CDN)
- Sign In button: `bg-gradient-to-r from-primary to-secondary text-on-primary rounded-full px-6 py-2 font-label-sm hover:shadow-lg hover:scale-105 transition-all`
- When authenticated: show user avatar (circular, 40px, border-2 border-primary-container)

**`Footer.tsx`** — matches all HTML designs:
- `bg-inverse-surface py-stack-lg`
- Brand `VividPass` in `text-section-heading text-surface`
- Links in `text-surface-variant font-caption hover:text-primary-fixed-dim`
- Copyright in `text-surface-variant font-caption`

**`EventCard.tsx`** — Bento grid card:
- `rounded-xl shadow-md hover:shadow-xl transition-shadow duration-300 overflow-hidden cursor-pointer`
- Date badge: `absolute top-4 left-4 bg-surface/90 backdrop-blur-sm px-3 py-1.5 rounded-lg` showing month + day
- Category badge: colored pills per category (Music=secondary-fixed, Sports=error-container, Comedy=surface-container-high, Theater=tertiary-fixed)
- Title: `font-section-heading text-on-surface group-hover:text-primary transition-colors`
- Price: `font-label-sm text-primary`

#### API Client
- `src/lib/api.ts` — Axios base config with JWT interceptor (reads from Zustand auth store)
- `src/lib/queryClient.ts` — React Query client config
- `src/store/authStore.ts` — Zustand store using **sessionStorage** (NOT localStorage — security requirement)

```typescript
// authStore.ts — sessionStorage only (interim M-008 fix)
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

interface AuthState {
  token: string | null;
  user: { id: number; email: string; role: string } | null;
  setAuth: (token: string, user: AuthState['user']) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      clearAuth: () => set({ token: null, user: null }),
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => sessionStorage), // sessionStorage — clears on tab close
    }
  )
);
```

#### Home Page (`app/page.tsx`) — matches Image 2 exactly

**Hero Section:**
- Full-width, `bg-[#0F172A]` dark background with concert image overlay (`opacity-40 mix-blend-overlay`)
- `py-section-gap` vertical padding
- Badge: `bg-primary-container/20 text-inverse-primary rounded-full px-4 py-1.5 backdrop-blur-md border border-primary/30` — "LIVE IN YOUR CITY"
- H1: `text-hero-headline-mobile md:text-hero-headline text-on-primary` — "Find events worth leaving the house for."
- Subheading: `text-surface-variant font-body-lg`
- Search bar: glassmorphism pill — `bg-surface/10 backdrop-blur-xl border border-surface/20 rounded-full` containing 3 inputs (search, city, date) each in `bg-surface rounded-full px-4 py-3`
- Search button: gradient pill

**Category Filter Row:**
- Horizontal scroll, `gap-3 pb-4 snap-x scrollbar-hide`
- Active pill: `bg-primary-fixed text-on-primary-fixed`
- Inactive pills: `bg-surface border border-outline-variant hover:border-primary hover:text-primary`

**"Upcoming Events" Bento Grid — `grid-cols-12`:**
- Featured card: `col-span-12 md:col-span-8` — horizontal layout (image left 60%, content right 40%)
- Regular cards: `col-span-12 md:col-span-4` — vertical layout with image top
- Fetch: `GET /api/events?status=PUBLISHED` with React Query (5-minute stale time)

### Evening (1 hr) — Wire Search + Git
- Category pill click triggers new API call with filter params
- Git commit: `feat: add nextjs frontend with Kinetic Premier design system and home page`

---

## Expected Deliverable / Success Criteria

```
[ ] Next.js app running on port 3000
[ ] tailwind.config.ts has full Kinetic Premier token set
[ ] Inter font loaded via next/font/google
[ ] Home page Hero section matches Image 2 (dark bg, glassmorphism search)
[ ] Category filter pills match Image 2 (scrollable, pill shape)
[ ] Event grid is Bento layout (featured card 8-col + regular 4-col)
[ ] EventCard has date badge, category chip, price in primary color
[ ] Navbar: fixed, glassmorphism, gradient Sign In button
[ ] Footer: inverse-surface bg, VividPass brand, footer links
[ ] Real event data fetched from Spring Boot (not mocked)
[ ] authStore uses sessionStorage (NOT localStorage)
[ ] No hardcoded localhost:8080 — all via NEXT_PUBLIC_API_URL
```

---

## Skills to Attach This Session
- Attach `nextjs.instructions.md` from `.github/instructions/` (auto-injected for `.tsx` files)

## ⚠️ Critical Reminders
1. **NEVER use localStorage** for JWT — use sessionStorage in authStore.ts (security requirement M-008)
2. The Tailwind config tokens must match the Kinetic Premier spec EXACTLY — do not invent new color names
3. The navbar must be a shared component (`Navbar.tsx`) reused across ALL 10 pages
4. The footer must be a shared component (`Footer.tsx`) reused across ALL 10 pages
5. The Home page hero background image should be a dark concert/event photo — no placeholder gradients
