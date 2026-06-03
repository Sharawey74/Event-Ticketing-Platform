# Day 15 — Session Prompt

**Date:** Friday, April 18, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 15 — Frontend: Organizer Dashboard + Auth Pages + Create Event Form.
Feature: frontend-organizer-dashboard

Active fixes today:
- No new overlay fixes today.
- Cross-cutting: Fix CC-2 (no hardcoded API URLs)

Pre-conditions confirmed:
- Day 14 complete: User dashboard and QR display working ✅
- middleware.ts covers /dashboard/* and /organizer/* ✅
- Docker Desktop + backend running ✅
- Kinetic Premier tailwind.config.ts applied ✅
- Navbar.tsx and Footer.tsx shared components exist ✅

No TDD gate for frontend. Begin with UI implementation directly.

Non-negotiable rules:
- All API calls must use NEXT_PUBLIC_API_URL.
- JWT token must be sent in Authorization header.
- Organizer routes must be protected (role === 'ORGANIZER').
- ALL styling must use Kinetic Premier tokens.
- Login/Register: E-001 security rule — the role field must NOT be on RegisterRequest sent to backend.
  The Register page UI can show Attendee/Organizer toggle but must never send role to the backend.
  Role assignment is backend-controlled only.

Start with: Organizer Events Dashboard at /organizer/events matching the Image 14 HTML design reference.
```

---

## Context Briefing

**What we're building today (4 pages):**

1. **Organizer Dashboard** (`/organizer/events`) — Image 14 reference
2. **Create Event Form** (`/organizer/events/new`) — Image 16 reference
3. **Login** (`/auth/login`) — Image 18 reference
4. **Register** (`/auth/register`) — Image 20 reference

**Check-in Logic:**
The backend `CHECK_IN` state machine transition is guarded by `CheckInGuard` which ensures the person triggering check-in is the organizer of the event. Frontend hits `POST /api/v1/bookings/{id}/check-ins`.

**Pre-conditions from Day 14:**

- User bookings list working ✅
- QR code rendering inline ✅
- middleware.ts route protection working ✅

---

## Design Reference (Image 14 — Organizer Dashboard)

**Layout:** `flex-grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full`

**Page Header:**

```tsx
<div className="flex flex-col md:flex-row md:items-center justify-between gap-stack-md mb-stack-lg">
  <div>
    <h1 className="font-hero-headline text-hero-headline text-on-surface">My Events</h1>
    <p className="font-body text-body text-on-surface-variant mt-2">Manage your upcoming venues and track performance.</p>
  </div>
  {/* "Create New Event" button: bg-primary text-on-primary rounded-full px-6 py-3 shadow-md hover:shadow-xl */}
  {/* add icon + "Create New Event" text */}
</div>
```

**Sales Chart Card:**

```tsx
<div className="bg-surface-container-lowest rounded-xl shadow-md p-stack-lg mb-stack-lg border border-outline-variant/30">
  <div className="flex justify-between items-center mb-stack-lg">
    <h2 className="font-section-heading text-section-heading text-on-surface">Sales Over Time</h2>
    {/* "Last 30 Days" dropdown button: text-on-surface-variant hover:text-primary */}
  </div>
  {/* SVG line chart area — h-[300px] */}
  {/* Y-axis labels: $0 to $10k */}
  {/* Line: stroke="#630ed4" stroke-width="4" */}
  {/* Area fill: linearGradient from #630ed4 opacity-0.2 to transparent */}
  {/* Data points: circle fill="#ffffff" r="6" stroke="#630ed4" stroke-width="3" */}
  {/* X-axis labels: Oct 1 to Oct 29 (font-caption text-outline) */}
</div>
```

**Events List section:**

```tsx
{/* Header: "Recent Venues & Events" + search input (rounded-full border border-outline-variant focus:ring-primary) */}
{/* Each event row: bg-surface-container-lowest rounded-xl shadow-md p-stack-md border border-outline-variant/30
    flex flex-col lg:flex-row lg:items-center gap-stack-md hover:shadow-xl transition-shadow */}
```

**Event Row anatomy (3-part flex):**

```tsx
{/* Part 1 — Event info (flex-1): thumbnail (w-32 h-20 rounded-lg) + name + date + status badge */}
{/* Status badge: bg-primary-fixed text-on-primary-fixed for "Active"; bg-surface-container-high text-on-surface-variant for "Draft" */}
{/* Part 2 — Sales progress bar (flex-1, border-l border-outline-variant/30): */}
{/* "Sold: X / Capacity: Y" text (font-caption text-on-surface-variant) */}
{/* Progress bar: w-full bg-surface-container-high rounded-full h-2; fill: bg-primary rounded-full width% */}
{/* Part 3 — Revenue + Actions (flex-1, border-l): */}
{/* "Gross Revenue" label + amount in font-label-sm text-body-lg font-bold */}
{/* Action buttons: w-10 h-10 rounded-full border border-outline-variant hover:text-primary hover:border-primary */}
{/* Icons: group (View Attendees) and edit (Edit Event) */}
```

---

## Design Reference (Image 16 — Create Event Form)

**Layout:** Single centered column `max-w-3xl mx-auto`

**Page header (centered):**

```tsx
<div className="mb-stack-lg text-center">
  <h1 className="font-hero-headline text-hero-headline text-on-background">Create New Event</h1>
  <p className="font-body-lg text-body-lg text-on-surface-variant">Provide the details to get your event published.</p>
</div>
```

**Multi-step progress indicator (4 steps):**

```tsx
{/* Step track: absolute horizontal line bg-surface-container-highest z-0 */}
{/* Progress fill: absolute w-1/4 bg-primary z-0 transition-all duration-300 */}
{/* Step circles: relative z-10, active=bg-primary text-on-primary, inactive=bg-surface-container-highest */}
{/* Labels: font-caption, active=text-primary, inactive=text-on-surface-variant */}
{/* Steps: 1-Basic Info, 2-Schedule, 3-Tickets, 4-Review */}
```

**Step 1 — Basic Info Form:**

```tsx
<div className="bg-surface-container-lowest rounded-xl shadow-md p-stack-lg border border-outline-variant/50">
  <h2 className="font-section-heading text-section-heading text-on-background mb-stack-md">Basic Information</h2>
  <form className="space-y-stack-md flex flex-col">
    {/* Event Title: rounded-lg border border-outline-variant bg-surface-bright focus:ring-2 focus:ring-primary */}
    {/* Description: textarea same styling, resize-y, rows=4 */}
    {/* Category radio chips: rounded-full border border-outline-variant */}
    {/* checked state: bg-primary-fixed text-primary border-primary */}
    {/* Cover Image upload: aspect-[16/9] rounded-lg border-2 border-dashed border-outline-variant */}
    {/* add_photo_alternate icon (text-4xl text-outline group-hover:text-primary) */}
    {/* "Next Step" button: bg-primary rounded-full px-8 py-3 shadow-md hover:shadow-xl */}
  </form>
</div>
```

---

## Design Reference (Image 18 — Login Page)

**No full Navbar** — minimal header with just logo link (absolute positioned):

```tsx
<header className="w-full py-6 px-edge-padding absolute top-0 left-0 flex justify-center sm:justify-start items-center z-50">
  <a className="text-section-heading font-section-heading text-primary tracking-tighter" href="/">VividPass</a>
</header>
```

**Background decorative gradient:**

```tsx
<div className="absolute inset-0 opacity-40" style={{
  backgroundImage: 'radial-gradient(circle at 80% 20%, rgba(99,14,212,0.08) 0%, transparent 40%), radial-gradient(circle at 20% 80%, rgba(75,65,225,0.08) 0%, transparent 40%)'
}} />
```

**Login Card:**

```tsx
<div className="w-full max-w-[420px] bg-surface-container-lowest rounded-xl shadow-lg relative z-10 p-stack-lg border border-surface-variant overflow-hidden">
  {/* Top gradient accent bar: absolute top-0 w-full h-1 bg-gradient-to-r from-primary to-secondary */}
  <h1 className="font-section-heading text-section-heading text-on-surface text-center">Sign in to your account</h1>
  <form className="space-y-stack-md">
    {/* Email input with mail icon (left): rounded-lg border border-outline-variant pl-10 focus:ring-2 focus:ring-primary */}
    {/* Password input with lock icon (left) + visibility toggle (right) */}
    {/* "Forgot password?" link: text-primary font-label-sm */}
    {/* Sign In button: bg-gradient-to-r from-primary to-secondary rounded-full py-3 hover:shadow-lg hover:-translate-y-[1px] */}
    {/* "Don't have an account? Register" link: text-primary font-bold */}
  </form>
</div>
```

**API call on login:**

```typescript
const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password }), // NEVER send role
});
const { data } = await res.json(); // { token, user: { id, email, role } }
useAuthStore.getState().setAuth(data.token, data.user);
// Redirect: role === 'ORGANIZER' → /organizer/events; else → /dashboard/bookings
```

---

## Design Reference (Image 20 — Register Page)

**Minimal header** — same logo-only pattern as Login.

**Decorative background blob:**

```tsx
<div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-primary-fixed-dim/30 rounded-full blur-[100px] -z-10" />
```

**Register Card** (`max-w-[420px] bg-surface-container-lowest rounded-xl shadow-lg border border-outline-variant/30`):

```tsx
<h1 className="font-section-heading text-section-heading text-on-surface">Create your account</h1>
<p className="font-body text-body text-on-surface-variant">Join VividPass to unlock premium experiences.</p>
<form className="flex flex-col gap-5">
  {/* Name grid: 2-column (First Name + Last Name) */}
  {/* Email: full width */}
  {/* Password + strength indicator: */}
  {/* 4 strength bars: h-1.5 rounded-full (filled=bg-primary, empty=bg-surface-variant) */}
  {/* "Medium strength" text in font-caption */}
  
  {/* Role Selection (Bento toggle): */}
  {/* 2-column grid: Attendee | Organizer */}
  {/* Each: border-2 border-outline-variant peer-checked:border-primary peer-checked:bg-primary-fixed/20 rounded-xl p-4 */}
  {/* Icon: confirmation_number (Attendee), storefront (Organizer) */}
  {/* NOTE: This role selection is UI ONLY — DO NOT send role to backend API */}
  
  {/* "Create Account" button: bg-gradient-to-r from-primary to-secondary rounded-full py-3.5 font-bold */}
</form>
{/* "Already have an account? Sign in" link */}
```

**⚠️ CRITICAL — E-001 Security Rule:**
The Register form shows a role toggle (Attendee/Organizer) in the UI per the design. However, **the role field MUST NOT be sent to the backend** `POST /api/v1/auth/register` endpoint. The backend assigns the default role automatically. Sending `role` from the client is a privilege escalation vulnerability.

```typescript
// CORRECT — do not include role in the request body:
const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/auth/register`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ firstName, lastName, email, password }), // role field OMITTED
});

// WRONG — never do this:
// body: JSON.stringify({ firstName, lastName, email, password, role }) ← SECURITY BUG
```

---

## Tasks (In Order)

### Morning (2 hrs) — Organizer Dashboard

Create `frontend/app/organizer/events/page.tsx` (Client Component):

```typescript
async function fetchOrganizerEvents(token: string) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/organizer/events`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  return res.json();  // events with sold/capacity/revenue stats
}
// Render: page header + "Create New Event" CTA + Sales chart + event rows list
```

Create `frontend/app/organizer/events/[id]/attendees/page.tsx`:

```typescript
// Fetch: GET /api/v1/organizer/events/{id}/attendees
// Display: search bar + attendee table
// Columns: Attendee Name, Email, Tier, Booking ID, Status, Action
// "Check In" button: visible for CONFIRMED only
// After check-in: badge changes to "Checked In" (ATTENDED state)

async function checkInTicket(bookingId: number, token: string) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/bookings/${bookingId}/check-ins`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` }
  });
  return res.json();
}
```

### Afternoon (2.5 hrs) — Login + Register + Create Event

Create `frontend/app/auth/login/page.tsx` — matches Image 18.
Create `frontend/app/auth/register/page.tsx` — matches Image 20.
Create `frontend/app/organizer/events/new/page.tsx` — matches Image 16 (multi-step form, Step 1 only for now).

### Evening (1.5 hrs) — Smoke Test + Git

- Login as ORGANIZER → redirected to `/organizer/events`
- Login as ATTENDEE → redirected to `/dashboard/bookings`
- Register as new user → account created → auto-login → redirect
- Organizer: navigate to event attendees → click "Check In" → state updates to ATTENDED
- Git commit: `feat: implement organizer dashboard, check-in flow, login, register, create event (Kinetic Premier design)`

---

## Expected Deliverable / Success Criteria

```
[ ] /organizer/events: page header with "Create New Event" gradient-free primary button
[ ] Sales chart area: SVG line with #630ed4 stroke, gradient fill, data point circles
[ ] Event rows: 3-part flex layout (info + progress bar + revenue + action buttons)
[ ] Progress bar: bg-primary fill, bg-surface-container-high track, rounded-full
[ ] "Active" badge: bg-primary-fixed text-on-primary-fixed rounded-full
[ ] "Draft" badge: bg-surface-container-high text-on-surface-variant rounded-full
[ ] /organizer/events/[id]/attendees: search + table with Check In button
[ ] Check-in button triggers POST and updates row to "Checked In" badge (ATTENDED)
[ ] /organizer/events/new: multi-step progress indicator (4 steps, step 1 filled)
[ ] Create Event form: category radio chips with checked:bg-primary-fixed styling
[ ] Image upload area: dashed border, add_photo_alternate icon, hover transitions
[ ] /auth/login: no full navbar (logo-only absolute header)
[ ] Login card: top gradient accent bar from-primary to-secondary h-1
[ ] Login inputs: icon prefix (mail, lock), visibility toggle on password
[ ] Sign In button: gradient pill hover:-translate-y-[1px]
[ ] /auth/register: decorative background blob (blur-[100px])
[ ] Register role toggle: Attendee/Organizer Bento cards with peer-checked styling
[ ] E-001 ENFORCED: role field NOT sent to /api/v1/auth/register backend endpoint
[ ] After login: redirect ORGANIZER → /organizer/events, ATTENDEE → /dashboard/bookings
[ ] Password strength indicator: 4 bars (filled=bg-primary)
[ ] No hardcoded API URLs — all via NEXT_PUBLIC_API_URL
```

---

## Skills to Attach This Session

- `Plans/skills/nextjs-frontend.SKILL.md`

## ⚠️ Critical Reminders

1. **E-001 — CRITICAL SECURITY RULE**: Do NOT send the `role` field to the backend register endpoint. The UI toggle is decorative — role is always assigned server-side as ATTENDEE by default.
2. Only users with role `ORGANIZER` should access `/organizer/*` routes — enforced by middleware.ts
3. The `POST /api/v1/bookings/{id}/check-ins` endpoint requires the user to be the event's organizer (enforced by `CheckInGuard` on the backend)
4. Login/Register pages suppress the main Navbar — use a minimal absolute-positioned logo header instead
5. The Sales chart in the Organizer Dashboard can use a simple SVG mock initially — data from a real chart library is a Day 19 polish item
6. **NEVER use localStorage** — sessionStorage only (M-008 interim fix)
