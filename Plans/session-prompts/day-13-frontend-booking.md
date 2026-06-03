# Day 13 — Session Prompt

**Date:** Wednesday, April 16, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE
>
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 13 — Frontend: Event Detail Page + Booking Flow + Confirmation.
Feature: frontend-event-detail-booking

Active fixes today:
- No new overlay fixes today. Verify no magic numbers or hardcoded URLs.
- Cross-cutting: Fix CC-2 (no hardcoded API URLs — all via NEXT_PUBLIC_API_URL env var)
- Security: M-009 — check if dangerouslySetInnerHTML is used; apply DOMPurify if yes
- Security: M-008 interim — authStore must use sessionStorage (NOT localStorage)

Pre-conditions confirmed:
- Day 12 complete: RefundService tests passing ✅
- PricingEngine wired into BookingService ✅
- POST /api/v1/bookings/{id}/refunds endpoint live ✅
- NEXT_PUBLIC_API_URL=http://localhost:8080 set in frontend/.env.local ✅
- Docker Desktop + backend running ✅
- Kinetic Premier tailwind.config.ts applied (from Day 4) ✅
- Navbar.tsx and Footer.tsx shared components exist ✅

No TDD gate for frontend (Vitest setup deferred to Day 16).
Begin with UI implementation immediately after reading the task plan.

Non-negotiable rules:
- All API base URLs must read from process.env.NEXT_PUBLIC_API_URL — never hardcode localhost:8080.
- Use Next.js App Router (app/ directory) — not Pages Router.
- Server Components for data fetching where possible; Client Components only where interactivity is needed.
- Stripe redirect uses the URL from backend checkoutUrl response — never construct it manually.
- ALL styling must use Kinetic Premier tokens (primary: #630ed4, secondary: #4b41e1, Inter font).
- Cards: rounded-xl shadow-md hover:shadow-xl. Buttons: rounded-full gradient or border pill.
- DO NOT use dangerouslySetInnerHTML for event description — use React JSX safely.

Start with: Event Detail page at /events/[id] matching the Image 6 HTML design reference.
```

---

## Context Briefing

**What we're building today (3 pages):**

1. **Event Detail** (`/events/[id]`) — Image 6 reference
2. **Secure Checkout** (Stripe-hosted page via redirect) — no custom page needed, Stripe handles it; but the checkout trigger UI is in Image 8
3. **Booking Confirmation** (`/bookings/[id]/confirmation`) — Image 12 reference

**The booking flow:**

1. User views `/events/[id]` — sees event details + available tiers (sticky right panel)
2. User selects tier + quantity → POST `/api/v1/bookings` → receives `bookingId` + state `RESERVED`
3. 15-minute reservation countdown timer appears (NOT 5 min — backend was updated)
4. User clicks "Proceed to Checkout" → POST `/api/v1/bookings/{id}/checkouts` → receives Stripe `checkoutUrl`
5. Browser redirects to Stripe-hosted payment page (test card: 4242 4242 4242 4242)
6. After payment, Stripe redirects to `/bookings/[id]/confirmation?session_id=...`
7. Confirmation page shows animated checkmark + booking ref + QR code tickets

**Pre-conditions from Day 12:**

- RefundService: 5/5 tests passing ✅
- PricingEngine integrated into BookingService.reserveTickets() ✅
- V11 migration applied cleanly ✅

---

## Design Reference (Image 6 — Event Detail Page)

**Layout:** 2-column grid `lg:grid-cols-12`

- **Left column** (`lg:col-span-7 xl:col-span-8`): event details
- **Right column** (`lg:col-span-5 xl:col-span-4`): sticky ticketing panel

**Cover Image area:**

```tsx
// Full-width cover image: h-[409px] md:h-[512px]
// Gradient overlay: bg-gradient-to-t from-background to-transparent
// Content overlaps image: -mt-32 relative z-10
```

**Breadcrumb nav:**

```tsx
<nav className="flex text-on-surface-variant font-caption text-caption mb-stack-md">
  Events > Music > [Event Name]
</nav>
```

**Category badges (chips):**

```tsx
<span className="px-4 py-1 bg-[#F5F3FF] text-primary rounded-full font-label-sm text-label-sm">
  Music Festival
</span>
```

**Meta Info Grid:** `grid-cols-2 bg-surface-container-lowest p-6 rounded-xl shadow-md`

- Icon circles: `w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center`
- Icons: Material Symbols calendar_month, location_on
- Labels: `font-label-sm text-label-sm text-on-surface`

**Sticky Ticket Panel** (Image 6 right column):

```tsx
<div className="sticky top-28 bg-surface-container-lowest rounded-xl shadow-xl p-6 border border-outline-variant/30">
  <h2 className="font-section-heading text-section-heading text-on-surface">Select Tickets</h2>
  {/* Selected tier: border-2 border-primary rounded-xl p-4 bg-primary/5 */}
  {/* Unselected tier: border border-outline-variant rounded-xl p-4 hover:border-primary/50 */}
  {/* Price: font-section-heading text-primary */}
  {/* Quantity selector: rounded-full bg-surface border border-outline-variant */}
  {/* CTA: btn-gradient = bg-gradient-to-r from-primary to-secondary, h-14 rounded-full */}
</div>
```

---

## Design Reference (Image 8 — Checkout Page / Order Summary Sidebar)

The "Secure Checkout" page layout (shown when user is about to complete a Stripe redirect):

**Left column (7/12) — Attendee Info form:**

```tsx
<section className="bg-surface-container-lowest rounded-xl shadow-md p-stack-lg border border-outline-variant/30">
  {/* Numbered step indicator: w-8 h-8 rounded-full bg-primary-container text-on-primary-container */}
  {/* Form inputs: bg-surface-container-low border border-outline-variant rounded-xl px-4 py-3 */}
  {/* focus:border-primary focus:ring-1 focus:ring-primary */}
</section>
```

**Right column (5/12) — Sticky Order Summary:**

```tsx
<div className="sticky top-[100px] bg-surface-container-lowest rounded-xl shadow-lg border border-outline-variant/20">
  {/* Event thumbnail: h-48 with gradient overlay to-black/80 */}
  {/* Event name overlaid on image: font-section-heading text-on-primary */}
  {/* Price breakdown lines: justify-between font-body text-on-surface-variant */}
  {/* Total: font-hero-headline-mobile text-primary */}
  {/* CTA: bg-gradient-to-r from-primary to-secondary rounded-full py-4 */}
</div>
```

Note: The "Checkout" page is Stripe-hosted. This component only shows the pre-checkout order summary view. The actual checkout is initiated by clicking "Proceed to Checkout" which calls the backend and redirects.

---

## Design Reference (Image 12 — Booking Confirmation Page)

**No TopNavBar** on this page — confirmation screens suppress navigation per design rule.

**Success Checkmark Animation:**

```css
/* SVG animated checkmark — primary color stroke */
.success-checkmark { animation: fill .4s ease-in-out .4s forwards, scale .3s ease-in-out .9s both; }
.checkmark__circle { stroke: #630ed4; animation: stroke 0.6s cubic-bezier(0.65, 0, 0.45, 1) forwards; }
.checkmark__check { animation: stroke 0.3s cubic-bezier(0.65, 0, 0.45, 1) 0.8s forwards; }
```

**Header area (centered):**

```tsx
<div className="text-center mb-stack-lg">
  {/* SVG checkmark */}
  <h1 className="font-hero-headline text-hero-headline text-primary">Booking Confirmed!</h1>
  <p className="font-body-lg text-body-lg text-on-surface-variant">You're all set for an amazing experience.</p>
  {/* Booking ref pill: bg-surface-container-high px-4 py-2 rounded-full border border-outline-variant */}
  {/* "VVD-8472-X9" in font-label-sm text-primary font-bold tracking-wider */}
  {/* Copy button next to ref */}
</div>
```

**Event Summary Bento Grid (`grid-cols-3`):**

```tsx
{/* Main event card: md:col-span-2 — image + event name + date + venue */}
{/* Payment card: md:col-span-1 — bg-gradient-to-br from-primary to-secondary text-on-primary */}
{/* Total Paid in font-hero-headline-mobile */}
{/* "Payment Successful" with check_circle icon */}
```

**Ticket Cards** (one per ticket — physical ticket style):

```tsx
<div className="bg-surface-container-lowest rounded-xl shadow-md flex flex-col sm:flex-row border border-outline-variant/30 overflow-hidden">
  {/* Left: QR area — bg-surface-container p-4, border-r border-dashed, torn-edge circles */}
  {/* QR code: shimmer loading → actual QR image after 2s */}
  {/* "Scan at entry" caption */}
  {/* Right: Ticket details — tier name, Ticket ID in monospace, Gate, Seat in 2-col grid */}
</div>
```

**QR Code rendering:**

```tsx
// Backend stores QR as Base64 PNG in tickets table
<img
  src={`data:image/png;base64,${ticket.qrCode}`}
  alt={`QR Code for ticket ${ticket.id}`}
  className="w-32 h-32"
/>
// Show shimmer skeleton while loading, then swap to actual QR
```

**Action Buttons:**

```tsx
{/* Primary: bg-gradient-to-r from-primary to-secondary rounded-full */}
{/* "Download Tickets (PDF)" + download icon */}
{/* Secondary: border border-primary text-primary rounded-full */}
{/* "Add to Google Calendar" + calendar_add_on icon */}
```

---

## Tasks (In Order)

### Morning (2 hrs) — Event Detail Page

Create `frontend/app/events/[id]/page.tsx` — Server Component:

```typescript
async function EventDetailPage({ params }: { params: { id: string } }) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/events/${params.id}`, {
    cache: 'no-store'  // always fresh — inventory counts change
  });
  const event = await res.json();
  return (
    // Cover image, breadcrumb, 2-column layout
    // Left: category badges, h1 title, meta grid, description
    // Right: <TicketTierSelector> (Client Component)
  );
}
```

`TicketTierSelector` — Client Component:

```typescript
'use client';
// Props: eventId, tiers (array of { id, name, price, available, description })
// State: selectedTierId, quantity
// Selected tier: border-2 border-primary bg-primary/5
// Unselected tier: border border-outline-variant hover:border-primary/50
// On "Add to Cart" / "Reserve": POST /api/v1/bookings (requires Idempotency-Key header) → { bookingId, state: "RESERVED", expiresAt }
// Show <ReservationTimer> after successful reservation
// On "Proceed to Checkout": POST /api/v1/bookings/{bookingId}/checkouts → { checkoutUrl }
//   window.location.href = checkoutUrl
```

### Afternoon (2.5 hrs) — Confirmation Page

Create `frontend/app/bookings/[id]/confirmation/page.tsx`:

```typescript
// Server Component — NO Navbar rendered on this page
async function ConfirmationPage({ params, searchParams }: Props) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/bookings/${params.id}`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  const booking = await res.json();
  return (
    // Animated SVG checkmark (CSS animation, primary color)
    // "Booking Confirmed!" h1 in text-primary
    // Booking reference pill (copy-to-clipboard)
    // Event summary bento grid (2+1 columns)
    // Ticket cards with QR shimmer → actual QR
    // Download + Calendar action buttons
  );
}
```

### Evening (1.5 hrs) — Smoke Test + M-009 Audit + Git

**M-009 Security Audit:**

- Search ALL `.tsx` files for `dangerouslySetInnerHTML`
- If found for event description: apply `isomorphic-dompurify`

  ```bash
  npm install isomorphic-dompurify @types/dompurify
  ```

  ```tsx
  import DOMPurify from 'isomorphic-dompurify';
  <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(event.description) }} />
  ```

- If NOT found (using JSX text rendering): no action needed — JSX is safe by default

**Full flow smoke test:**

1. `http://localhost:3000/` → click event card → event detail page loads
2. Select tier + quantity → timer appears → click "Proceed to Checkout"
3. Stripe page → test card `4242 4242 4242 4242` → complete
4. `/bookings/[id]/confirmation` → animated checkmark → QR codes render

Git commit: `feat: implement event detail page, booking flow, and confirmation page (Kinetic Premier design)`

---

## Expected Deliverable / Success Criteria

```
[ ] /events/[id] cover image fills full width (h-512px), gradient overlay fades to background
[ ] Breadcrumb: Events > Category > Event Name (text-on-surface-variant font-caption)
[ ] Category chips: rounded-full bg-[#F5F3FF] text-primary
[ ] Meta info grid: calendar + location with primary/10 icon circles
[ ] Sticky ticket panel: selected tier has border-2 border-primary bg-primary/5
[ ] Unselected tier: border border-outline-variant hover:border-primary/50
[ ] Price shows tier price + fee separately
[ ] Quantity stepper: rounded-full bg-surface border border-outline-variant
[ ] CTA button: bg-gradient-to-r from-primary to-secondary h-14 rounded-full
[ ] POST /api/v1/bookings (requires Idempotency-Key header) called with correct JWT auth header
[ ] Reservation timer shows countdown from expiresAt
[ ] Checkout redirects to Stripe-hosted payment page
[ ] Confirmation: no navbar (suppressed for transactional screen)
[ ] Confirmation: animated SVG checkmark (primary color, CSS animation)
[ ] Confirmation: booking ref in rounded-full pill with copy button
[ ] Confirmation: event summary bento (2-col event + 1-col gradient payment card)
[ ] Confirmation: ticket cards with QR shimmer → actual Base64 QR image
[ ] Confirmation: "Download Tickets" + "Add to Google Calendar" buttons
[ ] M-009: no dangerouslySetInnerHTML without DOMPurify; OR JSX text rendering used
[ ] No hardcoded localhost:8080 — all via NEXT_PUBLIC_API_URL
[ ] authStore uses sessionStorage (NOT localStorage)
[ ] Full flow tested end-to-end with Stripe test card 4242 4242 4242 4242
```

---

## Skills to Attach This Session

- `Plans/skills/nextjs-frontend.SKILL.md`

## ⚠️ Critical Reminders

1. **NEVER use localStorage** — authStore must use sessionStorage (M-008 interim fix from Day 4)
2. **M-009**: Check for `dangerouslySetInnerHTML` — apply DOMPurify only if found. NEVER strip HTML on the backend (destroys data).
3. **Confirmation page has NO navbar** — this is a deliberate UX decision for transactional screens
4. The Stripe redirect URL comes from the backend response — never construct it in the frontend
5. `expiresAt` is a UTC `Instant` — parse with `new Date(expiresAt)` in JS, display in user's local time
6. QR codes are `data:image/png;base64,...` — render inline with `<img src={...}>`, NOT as URL fetch
7. Use `cache: 'no-store'` on event fetch — inventory counts change constantly
8. **NEVER hardcode `localhost:8080`** — always `process.env.NEXT_PUBLIC_API_URL` (CC-2)
