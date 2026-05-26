# Day 15 — Session Prompt
**Date:** Friday, April 18, 2026 | **Planned Hours:** 6 hrs

---

## YOUR FIRST MESSAGE TO COPILOT
> After pasting `instructions.txt` content, send this as your next message:

```
We are on Day 15 — Frontend: Organizer Dashboard.
Feature: frontend-organizer-dashboard

Active fixes today:
- No new overlay fixes today.
- Cross-cutting: Fix CC-2 (no hardcoded API URLs)

Pre-conditions confirmed:
- Day 14 complete: User dashboard and QR display working ✅
- Docker Desktop + backend running ✅

No TDD gate for frontend. Begin with UI implementation directly.

Non-negotiable rules:
- All API calls must use NEXT_PUBLIC_API_URL.
- JWT token must be sent in Authorization header.
- Organizer routes must be protected (role === 'ORGANIZER').

Start with: Organizer events list page at /organizer/events.
```

---

## Context Briefing

**What we're building today:**
Day 15 builds the organizer experience. Event organizers need to see the events they manage, view revenue and sales statistics, list attendees, and most importantly: check them in (scanning QR codes or clicking a button). 

**Check-in Logic:**
The backend `CHECK_IN` state machine transition is guarded by `CheckInGuard` (Fix 8.2) which ensures that the person triggering the check-in is the organizer of the event. The frontend will hit `POST /api/bookings/{id}/check-in`.

**Pre-conditions from Day 14:**
- User bookings list working ✅
- QR code rendering inline ✅

---

## Active Plan Reference

- **Plan section:** Section 2 — Week 3, Day 15
- **Plan file to attach:** `Plans/Text/Phase1A_Section 2_ExecutionMap.txt`

---

## Fixes to Apply Today

No new overlay fixes. Enforce existing cross-cutting rules:

| Rule | Check |
| :--- | :--- |
| Fix CC-2 | No hardcoded API URLs |
| Auth | All `/api/organizer/*` calls include `Authorization: Bearer {token}` |

---

## Tasks (In Order)

### Morning (2 hrs) — Organizer Events & Stats

Create `frontend/app/organizer/events/page.tsx`:

```typescript
// Client Component
'use client';

async function fetchOrganizerEvents(token: string) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/organizer/events`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  return res.json();  // list of events owned by organizer with sold/capacity/revenue stats
}

// Display: table of events
// Columns: Title, Date, Tickets Sold, Total Revenue, Status, Actions (Manage)
```

### Afternoon (3 hrs) — Attendee List & Check-in

Create `frontend/app/organizer/events/[id]/attendees/page.tsx`:

```typescript
// Fetch bookings for the event (only CONFIRMED or ATTENDED)
async function fetchAttendees(eventId: number, token: string) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/organizer/events/${eventId}/attendees`, {
    headers: { Authorization: `Bearer ${token}` },
    cache: 'no-store',
  });
  return res.json(); // list of tickets/bookings
}

async function checkInTicket(bookingId: number, token: string) {
  const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/bookings/${bookingId}/check-in`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` }
  });
  return res.json();
}
```

**UI Layout:**
- Show a search bar (to search by booking ID or User Name).
- Show table of attendees.
- If ticket is `CONFIRMED`, show a "Check In" button.
- If ticket is `ATTENDED`, show "Checked In" badge.

### Evening (1 hr) — Smoke Test + Git

- Login as an Organizer (role `ORGANIZER`).
- Navigate to `/organizer/events`.
- Select an event with a confirmed booking.
- Click "Check In" on an attendee and verify the status updates to `ATTENDED`.
- Git commit: `feat: implement organizer dashboard and check-in flow`

---

## Expected Deliverable / Success Criteria

```
[ ] /organizer/events shows list of events managed by the user
[ ] Event stats (sold, capacity, revenue) display correctly
[ ] /organizer/events/[id]/attendees shows list of confirmed/attended bookings
[ ] "Check In" button calls POST /api/bookings/{id}/check-in
[ ] Check-in updates UI to show "Checked In" (ATTENDED state)
[ ] No hardcoded API URLs — all via NEXT_PUBLIC_API_URL
```

---

## Skills to Attach This Session
- `Plans/skills/nextjs-frontend.SKILL.md`

## ⚠️ Critical Reminders
1. Only users with role `ORGANIZER` should access these routes.
2. The `POST /api/bookings/{id}/check-in` endpoint requires the user to be the organizer of the event (enforced by `CheckInGuard`).
