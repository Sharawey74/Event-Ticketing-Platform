# Day 14 — Session Prompt

**Date:** Thursday, April 17, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 14 — Frontend: User Dashboard + QR Code Display.
Feature: frontend-user-dashboard

Active fixes today:
- No new overlay fixes today.
- Cross-cutting: Fix CC-2 (no hardcoded URLs — all via NEXT_PUBLIC_API_URL)
- Security: M-008 interim — verify authStore uses sessionStorage everywhere

Pre-conditions confirmed:
- Day 13 complete: Full booking flow (reserve → Stripe → confirmation) working ✅
- Confirmation page renders QR code inline ✅
- Docker Desktop + backend running ✅
- NEXT_PUBLIC_API_URL set in frontend/.env.local ✅
- Kinetic Premier tailwind.config.ts applied ✅
- Navbar.tsx and Footer.tsx shared components exist ✅

No TDD gate for frontend. Begin with UI implementation directly.

Non-negotiable rules:
- All API calls must use NEXT_PUBLIC_API_URL — never hardcode localhost:8080.
- JWT token must be sent in Authorization header for all authenticated endpoints.
- QR codes are Base64 PNG strings from backend — render as <img src="data:image/png;base64,...">
- Use Next.js App Router (app/ directory) — not Pages Router.
- ALL styling must use Kinetic Premier tokens. Booking status badges use design-system colors only.

Start with: User Dashboard at /dashboard/bookings matching the Image 10 HTML design reference.
```

---

## Context Briefing

**What we're building today (1 page + 1 sub-page):**

1. **User Dashboard** (`/dashboard/bookings`) — Image 10 reference
2. **Booking Detail** (`/dashboard/bookings/[id]`) — extends Image 12 confirmation style

**Pre-conditions from Day 13:**

- Event detail page + tier selector working ✅
- Full Stripe checkout → confirmation flow working ✅
- QR code displays inline on confirmation page ✅

---

## Design Reference (Image 10 — User Dashboard)

**Navbar:** Active link is "Dashboard" (`font-bold border-b-2 border-primary`) — update Navbar.tsx to accept an `activePage` prop.

**Main layout:** `pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen flex flex-col gap-section-gap`

### Profile + Stats Section (`grid-cols-12`)

**Profile Card** (`lg:col-span-4`):

```tsx
<div className="bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high
                flex flex-col items-center justify-center text-center relative overflow-hidden">
  {/* Gradient banner strip: absolute top-0 bg-gradient-to-r from-primary-container to-secondary opacity-20 h-24 */}
  {/* Avatar: w-24 h-24 rounded-full border-4 border-surface-container-lowest shadow-md */}
  {/* Name: font-section-heading text-on-surface */}
  {/* Email: font-body text-on-surface-variant */}
  {/* "Edit Profile" button: rounded-full border border-primary text-primary hover:bg-primary-container */}
</div>
```

**Stats Grid** (`lg:col-span-8`, 3 cards `sm:grid-cols-3`):

```tsx
{/* Each stat card: bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high
    hover:shadow-xl transition-shadow duration-300 */}
{/* Icon circle: w-12 h-12 rounded-full bg-primary-container/20 text-primary (or secondary, tertiary) */}
{/* Icons: confirmation_number, event_upcoming, account_balance_wallet */}
{/* Number: font-hero-headline-mobile text-on-surface */}
{/* Label: font-body text-on-surface-variant */}
```

Stats to display (fetched from backend):

- **Total Bookings** — count of all bookings
- **Upcoming Events** — count of CONFIRMED bookings with future event dates
- **Total Spent** — sum of all CONFIRMED booking amounts

### Upcoming Events Section

`grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-gutter`

**Upcoming Event Card:**

```tsx
<div className="bg-surface-container-lowest rounded-xl overflow-hidden shadow-md group
                hover:shadow-xl transition-all duration-300 cursor-pointer flex flex-col">
  {/* Image: h-48 object-cover group-hover:scale-105 transition-transform duration-500 */}
  {/* Category badge (top-right): bg-surface-container-lowest/90 backdrop-blur-sm px-3 py-1.5 rounded-full */}
  {/* Content: p-stack-md flex flex-col justify-between border-x border-b border-surface-container-highest rounded-b-xl */}
  {/* Date: flex items-center gap-2 text-primary font-label-sm — calendar_today icon */}
  {/* Event name: font-body-lg font-bold text-on-surface line-clamp-1 */}
  {/* Venue: font-caption text-on-surface-variant — location_on icon */}
  {/* Bottom row: "2 Tickets" pill + "View Pass" button (bg-primary text-on-primary rounded-full) */}
</div>
```

### Booking History Section

**Section header:** `flex justify-between items-end mb-stack-md`

- Title: `font-section-heading text-section-heading text-on-surface`
- Filter button: `w-10 h-10 rounded-full border border-surface-container-highest hover:bg-surface-container-low`

**History table:** `bg-surface-container-lowest rounded-xl shadow-md border border-surface-container-high overflow-hidden`

```tsx
<table className="w-full text-left border-collapse">
  <thead>
    {/* bg-surface-container-low text-on-surface-variant */}
    {/* Columns: Event, Date, Tier, Qty, Total, Status */}
    {/* font-label-sm uppercase tracking-wider */}
  </thead>
  <tbody>
    {/* Rows: border-b border-surface-container-high hover:bg-surface-container/50 */}
    {/* Event cell: bold name + caption venue below */}
  </tbody>
</table>
```

**BookingStatusBadge Component** — use design-system colors only:

```typescript
function BookingStatusBadge({ state }: { state: string }) {
  // Map backend states to Kinetic Premier color tokens:
  const styleMap: Record<string, string> = {
    RESERVED: 'bg-secondary-fixed text-on-secondary-fixed',          // purple-ish
    PAYMENT_PENDING: 'bg-tertiary-fixed text-on-tertiary-fixed',     // blue-grey
    CONFIRMED: 'bg-[#e6f4ea] text-[#137333]',                        // green (matches Image 10)
    ATTENDED: 'bg-[#e6f4ea] text-[#137333]',                         // green "Attended"
    EXPIRED: 'bg-error-container text-on-error-container',           // red
    CANCELLED: 'bg-error-container text-on-error-container',         // red
    REFUND_REQUESTED: 'bg-primary-fixed text-on-primary-fixed',      // violet
    REFUND_APPROVED: 'bg-primary-fixed text-on-primary-fixed',
    REFUND_DENIED: 'bg-surface-container-high text-on-surface-variant',
    PAYMENT_FAILED: 'bg-error-container text-on-error-container',
  };
  // Badge: inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold
  // Dot: w-1.5 h-1.5 rounded-full (matching background color darker variant)
  const cls = styleMap[state] ?? 'bg-surface-container text-on-surface-variant';
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${cls}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current opacity-70" />
      {state.replace('_', ' ')}
    </span>
  );
}
```

**"Load More History" button:** `text-primary font-label-sm flex items-center gap-1 hover:text-on-primary-fixed-variant group`

---

## Tasks (In Order)

### Morning (2 hrs) — User Dashboard

Create `frontend/app/dashboard/bookings/page.tsx`:

```typescript
'use client';

// Fetch summary stats: total bookings, upcoming count, total spent
// Fetch upcoming events: GET /api/v1/bookings/my?status=CONFIRMED&future=true
// Fetch all bookings for history table: GET /api/v1/bookings/my
// Render: Profile card + 3 stat cards + upcoming grid + history table

async function fetchMyBookings(token: string) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/bookings/my`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  return res.json();  // { data: [{ id, state, eventTitle, eventDate, venueName, tickets, totalAmount }] }
}
```

**Auth guard via middleware:**

```typescript
// middleware.ts
export function middleware(request: NextRequest) {
  const token = request.cookies.get('token')?.value
    ?? request.headers.get('authorization')?.replace('Bearer ', '');
  if (!token && request.nextUrl.pathname.startsWith('/dashboard')) {
    return NextResponse.redirect(new URL('/auth/login', request.url));
  }
}
export const config = { matcher: ['/dashboard/:path*', '/organizer/:path*'] };
```

### Afternoon (3 hrs) — Booking Detail + Refund Action

Create `frontend/app/dashboard/bookings/[id]/page.tsx`:

```typescript
// Show full booking info: event, venue, date, tier, quantity, total
// Show individual TicketCard components (each with QR code)
// Show refund button only if state === 'CONFIRMED' and event > 3 days away

function TicketCard({ ticket }: { ticket: Ticket }) {
  return (
    // Style matches Image 12 ticket card:
    // rounded-xl shadow-md flex flex-col sm:flex-row border border-outline-variant/30
    // Left: QR area — bg-surface-container p-4 w-full sm:w-48 border-dashed
    // Torn-edge: corner circles using bg-background rounded-full
    // Right: VIP Access badge, Ticket ID (font-mono), Gate + Seat 2-col grid
    <div className="bg-surface-container-lowest rounded-xl shadow-md flex flex-col sm:flex-row border border-outline-variant/30 overflow-hidden">
      <div className="w-full sm:w-48 bg-surface-container p-4 flex flex-col items-center justify-center border-b sm:border-b-0 sm:border-r border-outline-variant/30 border-dashed">
        <img
          src={`data:image/png;base64,${ticket.qrCode}`}
          alt={`QR Code for ticket ${ticket.id}`}
          className="w-32 h-32 rounded-lg"
        />
        <p className="font-caption text-caption text-on-surface-variant text-center mt-2">Scan at entry</p>
      </div>
      <div className="p-stack-md flex-grow flex flex-col justify-between">
        <span className="bg-surface-tint/10 text-primary font-label-sm px-2 py-1 rounded uppercase tracking-wide">
          {ticket.tierName}
        </span>
        <div className="grid grid-cols-2 gap-4 pt-4 border-t border-outline-variant/30 mt-4">
          <div><p className="font-caption text-on-surface-variant">Gate</p><p className="font-body text-on-surface">{ticket.gate ?? 'Main'}</p></div>
          <div><p className="font-caption text-on-surface-variant">Seat</p><p className="font-body text-on-surface">{ticket.seatNumber ?? 'General Admission'}</p></div>
        </div>
      </div>
    </div>
  );
}
```

**Refund Request Action:**

```typescript
async function requestRefund(bookingId: number, token: string) {
  const res = await fetch(
    `${process.env.NEXT_PUBLIC_API_URL}/api/v1/bookings/${bookingId}/refunds`,
    { method: 'POST', headers: { Authorization: `Bearer ${token}` } }
  );
  return res.json();
  // Show inline: "Refund of $X processed" or "Refund denied: {reason}"
  // Refund button: border border-error text-error rounded-full hover:bg-error-container
}
```

### Evening (1 hr) — UX Polish + Git

- Group upcoming vs past events in the dashboard (two subsections)
- Add empty state for no upcoming bookings (friendly illustration + "Discover Events" CTA)
- Git commit: `feat: implement user dashboard with booking list, QR display, and refund action (Kinetic Premier design)`

---

## Expected Deliverable / Success Criteria

```
[ ] /dashboard/bookings: Profile card with gradient banner strip + avatar
[ ] 3 stat cards: correct icons, font-hero-headline-mobile numbers, hover:shadow-xl
[ ] Upcoming events grid: card image zoom on hover, category badge, "View Pass" button
[ ] History table: header bg-surface-container-low, alternating row hover
[ ] BookingStatusBadge: uses Kinetic Premier tokens (no raw Tailwind yellow/blue/green)
[ ] CONFIRMED / ATTENDED badge: bg-[#e6f4ea] text-[#137333] with dot
[ ] CANCELLED badge: bg-error-container text-on-error-container with dot
[ ] /dashboard/bookings/[id]: ticket cards match Image 12 torn-edge style
[ ] QR code renders as inline Base64 image (not a URL fetch)
[ ] Refund button visible for CONFIRMED bookings only (not EXPIRED, not ATTENDED)
[ ] Refund result displays inline (approved/partial/denied with amount or reason)
[ ] Auth middleware redirects unauthenticated users to /auth/login
[ ] middleware.ts covers /dashboard/* AND /organizer/* paths
[ ] No hardcoded API URLs — all via NEXT_PUBLIC_API_URL
[ ] authStore verified using sessionStorage (NOT localStorage)
```

---

## Skills to Attach This Session

- `Plans/skills/nextjs-frontend.SKILL.md`

## ⚠️ Critical Reminders

1. **BookingStatusBadge MUST use Kinetic Premier tokens** — not raw Tailwind green/yellow/red
2. **NEVER use localStorage** — sessionStorage only (M-008 interim fix)
3. Show the refund button ONLY for CONFIRMED state — other states should show read-only status badge
4. Expired reservations (RESERVED + past `expiresAt`) must still appear in history table — state is EXPIRED, not hidden
5. The stat cards derive from the bookings data already fetched — no extra API call needed for counts
6. QR code is already stored in `tickets` table as Base64 — no regeneration needed on client

---

## 📋 Scope Analysis Reference

> **Full scope analysis (what is in/out of scope for Days 13–21):**
> `docs/Core/day13-21-scope-analysis.md`

### Priority Items Active This Day

| ID | Priority | Item | Status |
|----|----------|------|--------|
| HIGH-10 | 🟠 P2 | Create `frontend/src/middleware.ts` — protect `/dashboard/*` and `/organizer/*` paths; redirect unauthenticated users to `/auth/login` | 🔲 Required |
| MEDIUM-17 | 🟡 P3 | QR code rendered as `<img src={qrBase64} />` only — never `console.log(qrBase64)` anywhere in dashboard or ticket card components | 🔲 Required |

### Items Confirmed Out of Scope for Day 14

| Item | Why |
|------|-----|
| Middleware as security enforcement | Middleware is UX only; backend returns 401/403 for unauthorized access — backend is the source of truth |
| HttpOnly cookie migration | Phase 1B only |
| Replacing `sessionStorage` with HttpOnly cookie | Phase 1B only |
| Removing pgAdmin from docker-compose | Local dev tools stay; only needs a README note (handled Day 17) |
