# Day 13 — Session Prompt
**Date:** Wednesday, April 16, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 13 — Frontend: Event Detail Page + Booking Flow.
Feature: frontend-event-detail-booking

Active fixes today:
- No new overlay fixes today. Verify no magic numbers or hardcoded URLs.
- Cross-cutting: Fix CC-2 (no hardcoded API URLs — all via NEXT_PUBLIC_API_URL env var)

Pre-conditions confirmed:
- Day 12 complete: RefundService tests passing ✅
- PricingEngine wired into BookingService ✅
- POST /api/bookings/{id}/refund endpoint live ✅
- NEXT_PUBLIC_API_URL=http://localhost:8080 set in frontend/.env.local ✅
- Docker Desktop + backend running ✅

No TDD gate for frontend (Vitest setup deferred to Day 16).
Begin with UI implementation immediately after reading the task plan.

Non-negotiable rules:
- All API base URLs must read from process.env.NEXT_PUBLIC_API_URL — never hardcode localhost:8080.
- Use Next.js App Router (app/ directory) — not Pages Router.
- Server Components for data fetching where possible; Client Components only where interactivity is needed.
- Stripe redirect uses the URL from backend checkoutUrl response — never construct it manually.

Start with: Event Detail page at /events/[id] with ticket tier selector.
```

---

## Context Briefing

**What we're building today:**
Day 13 builds the full purchase funnel in Next.js: Event Detail page → Ticket Tier selection → Stripe Checkout redirect → Booking Confirmation page with QR code. This is the most user-visible day of Week 2.

**Why NEXT_PUBLIC_API_URL matters:**
`NEXT_PUBLIC_` variables are baked into the Next.js bundle at build time. If you hardcode `localhost:8080`, the Vercel build will ship a frontend that tries to reach your laptop. Always read from `process.env.NEXT_PUBLIC_API_URL`.

**The booking flow:**
1. User views `/events/[id]` — sees event details + available tiers with dynamic pricing applied.
2. User selects tier + quantity → POST `/api/bookings` → receives `bookingId` + state `RESERVED`.
3. User clicks "Checkout" → POST `/api/bookings/{id}/checkout` → receives Stripe `checkoutUrl`.
4. Browser redirects to Stripe-hosted payment page.
5. After payment, Stripe redirects to `/bookings/[id]/confirmation?session_id=...`.
6. Confirmation page shows booking details + QR code (from `tickets[].qrCode` in the response).

**Pre-conditions from Day 12:**
- RefundService: 5/5 tests passing ✅
- PricingEngine integrated into BookingService.reserveTickets() ✅
- V11 migration applied cleanly ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 2, Day 13
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

No new overlay fixes. Enforce cross-cutting rules:

| Rule | Check |
| :--- | :--- |
| Fix CC-2 | No hardcoded API URLs — always `process.env.NEXT_PUBLIC_API_URL` |
| Fix CC-2 | No magic numbers in pricing display logic |
| Environment | `frontend/.env.local` must contain `NEXT_PUBLIC_API_URL=http://localhost:8080` |

---

## Tasks (In Order)

### Morning (2 hrs) — Event Detail Page

Create `frontend/app/events/[id]/page.tsx` — Server Component:

```typescript
// Server Component — fetches data at request time (no useEffect)
async function EventDetailPage({ params }: { params: { id: string } }) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/events/${params.id}`, {
    cache: 'no-store'  // always fresh — event data changes (inventory counts)
  });
  const event = await res.json();

  return (
    <div>
      <h1>{event.title}</h1>
      <p>{event.description}</p>
      <p>{event.venueName} — {new Date(event.startDate).toLocaleDateString()}</p>
      <TicketTierSelector eventId={event.id} tiers={event.ticketTiers} />
    </div>
  );
}
```

`TicketTierSelector` — Client Component (needs state for quantity + booking action):

```typescript
'use client';
// Props: eventId, tiers (array of { id, name, price, available })
// State: selectedTierId, quantity
// On "Reserve": POST /api/bookings → { bookingId, state: "RESERVED" }
// On "Checkout": POST /api/bookings/{bookingId}/checkout → { checkoutUrl }
//                window.location.href = checkoutUrl (Stripe redirect)
```

### Afternoon (3 hrs) — Booking Flow + Confirmation

#### Reservation API Hook

```typescript
// hooks/useBooking.ts
export function useBooking() {
  const reserve = async (tierId: number, quantity: number) => {
    const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/bookings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${getToken()}` },
      body: JSON.stringify({ tierId, quantity }),
    });
    return res.json();  // { data: { id, state, expiresAt } }
  };

  const checkout = async (bookingId: number) => {
    const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/bookings/${bookingId}/checkout`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${getToken()}` },
    });
    const data = await res.json();
    window.location.href = data.data.checkoutUrl;  // Stripe redirect
  };

  return { reserve, checkout };
}
```

#### Reservation Timer Component

```typescript
// After reserving, show countdown to expiry
// booking.expiresAt is an ISO string from backend (Instant serialized to UTC)
// 5-minute window = BusinessConstants.RESERVATION_TTL_SECONDS = 300

function ReservationTimer({ expiresAt }: { expiresAt: string }) {
  const [secondsLeft, setSecondsLeft] = useState(
    Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000)
  );
  // useEffect countdown — redirect to /events/[id] when expired
}
```

#### Confirmation Page (`/bookings/[id]/confirmation`)

Server Component — fetches booking by ID after Stripe redirect:

```typescript
// Shows:
// - Event name, date, venue
// - Tickets: tier name, seat, QR code (Base64 image inline)
// - Total paid
// QR code: <img src={`data:image/png;base64,${ticket.qrCode}`} alt="QR Code" />
```

### Evening (1 hr) — Smoke Test + Git

- Full purchase flow manual test:
  1. Browse to `http://localhost:3000/events`
  2. Click any event → see detail page with tiers
  3. Select 1 ticket → click Reserve → see 5-min timer
  4. Click Checkout → Stripe page with test card `4242 4242 4242 4242`
  5. Complete payment → confirmation page with QR code ✅
- Git commit: `feat: implement event detail page, booking flow, and confirmation page`

---

## Expected Deliverable / Success Criteria

```
[ ] /events/[id] renders event details + ticket tiers with prices (dynamic pricing applied)
[ ] TicketTierSelector allows quantity selection and reserve action
[ ] POST /api/bookings called with correct JWT auth header
[ ] Reservation timer shows countdown (5-minute window from expiresAt)
[ ] Checkout redirects to Stripe-hosted payment page
[ ] Stripe success redirects to /bookings/[id]/confirmation
[ ] Confirmation page shows QR code as inline Base64 image
[ ] No hardcoded localhost:8080 — all via NEXT_PUBLIC_API_URL
[ ] Full flow tested with Stripe test card 4242 4242 4242 4242
```

---

## Skills to Attach This Session
- `Plans/skills/nextjs-frontend.SKILL.md`

## ⚠️ Critical Reminders
1. **NEVER hardcode `localhost:8080`** — always `process.env.NEXT_PUBLIC_API_URL` (CC-2)
2. The Stripe redirect URL comes from the backend response — never construct it in the frontend
3. `expiresAt` is a UTC `Instant` — parse with `new Date(expiresAt)` in JS, display in local time
4. QR codes are `data:image/png;base64,...` — render inline with `<img src={...}>`
5. Use `cache: 'no-store'` on event fetch — inventory counts change constantly
