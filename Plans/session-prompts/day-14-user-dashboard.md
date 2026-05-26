# Day 14 — Session Prompt
**Date:** Thursday, April 17, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 14 — Frontend: User Dashboard + QR Code Display.
Feature: frontend-user-dashboard

Active fixes today:
- No new overlay fixes today.
- Cross-cutting: Fix CC-2 (no hardcoded URLs — all via NEXT_PUBLIC_API_URL)

Pre-conditions confirmed:
- Day 13 complete: Full booking flow (reserve → Stripe → confirmation) working ✅
- Confirmation page renders QR code inline ✅
- Docker Desktop + backend running ✅
- NEXT_PUBLIC_API_URL set in frontend/.env.local ✅

No TDD gate for frontend. Begin with UI implementation directly.

Non-negotiable rules:
- All API calls must use NEXT_PUBLIC_API_URL — never hardcode localhost:8080.
- JWT token must be sent in Authorization header for all authenticated endpoints.
- QR codes are Base64 PNG strings from backend — render as <img src="data:image/png;base64,...">
- Use Next.js App Router (app/ directory) — not Pages Router.

Start with: User bookings list page at /dashboard/bookings.
```

---

## Context Briefing

**What we're building today:**
Day 14 builds the authenticated user experience: a dashboard showing all bookings, QR code display for confirmed tickets, and check-in status. The backend APIs for these already exist (from Day 8–12). Today is pure frontend.

**Design Ticketmaster exercise:**
After completing the dashboard, spend 30 minutes studying [ticketmaster.com](https://www.ticketmaster.com) — analyse how they display booking lists, ticket details, and QR codes. Note the information hierarchy and apply those insights to improve the layout.

**Pre-conditions from Day 13:**
- Event detail page + tier selector working ✅
- Full Stripe checkout → confirmation flow working ✅
- QR code displays inline on confirmation page ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 2, Day 14
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

No new overlay fixes. Enforce existing cross-cutting rules:

| Rule | Check |
| :--- | :--- |
| Fix CC-2 | No hardcoded API URLs |
| Auth | All `/api/bookings/my` calls include `Authorization: Bearer {token}` |

---

## Tasks (In Order)

### Morning (2 hrs) — User Bookings List

Create `frontend/app/dashboard/bookings/page.tsx` — requires authentication:

```typescript
// Client Component — needs JWT from local storage/cookie
'use client';

async function fetchMyBookings(token: string) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/bookings/my`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  return res.json();  // { data: [{ id, state, eventTitle, eventDate, tickets, totalAmount }] }
}

// Display: table or card list
// Columns: Event, Date, Tickets, Total, Status (colored badge), Actions
// Status badge colors:
//   RESERVED → yellow (with countdown timer if expiresAt in future)
//   PAYMENT_PENDING → blue
//   CONFIRMED → green
//   ATTENDED → gray
//   EXPIRED → red
//   REFUND_REQUESTED / REFUND_APPROVED / REFUND_DENIED → purple variants
//   CANCELLED → dark red
```

#### Booking Status Badge Component

```typescript
function BookingStatusBadge({ state }: { state: string }) {
  const colorMap: Record<string, string> = {
    RESERVED: 'bg-yellow-100 text-yellow-800',
    PAYMENT_PENDING: 'bg-blue-100 text-blue-800',
    CONFIRMED: 'bg-green-100 text-green-800',
    ATTENDED: 'bg-gray-100 text-gray-600',
    EXPIRED: 'bg-red-100 text-red-700',
    CANCELLED: 'bg-red-200 text-red-900',
    REFUND_REQUESTED: 'bg-purple-100 text-purple-700',
    REFUND_APPROVED: 'bg-purple-200 text-purple-900',
    REFUND_DENIED: 'bg-orange-100 text-orange-800',
    PAYMENT_FAILED: 'bg-red-100 text-red-600',
  };
  return <span className={`px-2 py-1 rounded text-xs font-semibold ${colorMap[state] ?? ''}`}>{state}</span>;
}
```

### Afternoon (3 hrs) — Ticket Detail + QR Display + Refund Action

#### Booking Detail Page (`/dashboard/bookings/[id]`)

```typescript
// Shows:
// - Full booking info (event, venue, date, tier, quantity, total)
// - Individual ticket cards — each with: seat number, QR code image
// - Refund button (visible only if state = CONFIRMED and event > 3 days away)

function TicketCard({ ticket }: { ticket: Ticket }) {
  return (
    <div className="border rounded p-4">
      <p>Ticket #{ticket.id} — {ticket.tierName}</p>
      <img
        src={`data:image/png;base64,${ticket.qrCode}`}
        alt={`QR Code for ticket ${ticket.id}`}
        className="w-48 h-48 mt-2"
      />
      <p className="text-sm text-gray-500 mt-1">Seat: {ticket.seatNumber ?? 'General Admission'}</p>
    </div>
  );
}
```

#### Refund Request Action

```typescript
async function requestRefund(bookingId: number, token: string) {
  const res = await fetch(
    `${process.env.NEXT_PUBLIC_API_URL}/api/bookings/${bookingId}/refund`,
    { method: 'POST', headers: { Authorization: `Bearer ${token}` } }
  );
  const data = await res.json();
  // Show result: approved (amount), partial (amount), or denied (reason)
  return data;
}
```

Show refund result inline:
- **Approved**: "Your refund of $X has been processed. Allow 5–10 business days."
- **Denied**: "Refund denied: {reason}"

#### Navigation + Auth Guard

```typescript
// middleware.ts — redirect unauthenticated users to /login
export function middleware(request: NextRequest) {
  const token = request.cookies.get('token')?.value;
  if (!token && request.nextUrl.pathname.startsWith('/dashboard')) {
    return NextResponse.redirect(new URL('/login', request.url));
  }
}
export const config = { matcher: ['/dashboard/:path*'] };
```

### Evening (1 hr) — Design Study + Git

- Spend 30 minutes studying Ticketmaster's booking list UX — note their information hierarchy.
- Apply one UX improvement to the dashboard (e.g., group by upcoming vs past events).
- Git commit: `feat: implement user dashboard with booking list and QR code display`

---

## Expected Deliverable / Success Criteria

```
[ ] /dashboard/bookings shows all user bookings with status badges
[ ] Status badge colors match booking state (CONFIRMED=green, EXPIRED=red, etc.)
[ ] /dashboard/bookings/[id] shows individual ticket QR codes
[ ] QR code renders as inline Base64 image (not a URL fetch)
[ ] Refund button visible for CONFIRMED bookings only
[ ] Refund result displays inline (approved/partial/denied with amount or reason)
[ ] Auth middleware redirects unauthenticated users to /login
[ ] No hardcoded API URLs — all via NEXT_PUBLIC_API_URL
```

---

## Skills to Attach This Session
- `Plans/skills/nextjs-frontend.SKILL.md`

## ⚠️ Critical Reminders
1. **Never hardcode `localhost:8080`** — always `process.env.NEXT_PUBLIC_API_URL`
2. QR code is already stored in the `tickets` table as Base64 — no need to regenerate
3. Show the refund button ONLY for CONFIRMED state — other states should show read-only status
4. Expired reservations (RESERVED + past `expiresAt`) should still be displayed — state is EXPIRED, not hidden
