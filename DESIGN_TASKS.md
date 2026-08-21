# Eventora — Design Enhancement Plan

> **Scope:** the Next.js app under `frontend/`. Not the GitHub Pages showcase site under `site/`,
> which has its own separate dark design system and is already finished.
>
> **File name note:** this is `DESIGN_TASKS.md`, not `tasks.md`, because a root `TASKS.md`
> already exists (the personal cross-project tracker) and Windows treats the two names as the
> same file.

---

## 1. The governing constraint

Stated up front, because every decision below is measured against it:

> Adapt to the existing typography and colour palette. A few changes, **not an entire
> replacement.**

This matters because **three of the four supplied screens violate it**, and one honours it. That
is the single most important finding in this document, so it is addressed before any task list.

---

## 2. What was actually supplied

`frontend/NEW.DESIGN/` contains four screens and two design-system specs. They are **not one
coherent direction** — they are three:

| Screen | Theme | Type | Primary | System |
|---|---|---|---|---|
| `enhanced_organizer_dashboard_visuals` | **Light** | PNG only | Violet (current) | — |
| `eventora_landing_hero_og_colors` | Dark | PNG + code | Violet `#d2bbff` + neon | Neon Glass |
| `eventora_landing_hero_refined` | Dark | PNG + code | **Cyan** `#00f0ff` | Lumina Event Horizon |
| `other/eventora_organizer_dashboard_refined` | Dark | PNG + code | **Cyan** `#00f0ff` | Lumina Event Horizon |

### The three directions, ranked against the constraint

**Direction A — `enhanced_organizer_dashboard_visuals` (light).**
Keeps the current light surfaces, the current violet primary, and Inter. What changes is purely
*visual richness*: a gradient-filled area chart with real axis labels and a hover tooltip, softer
card elevation, better-styled `DRAFT`/`PUBLISHED` badges, a cart icon and avatar in the nav.
**This is the only screen that matches what you asked for.** It is also the lowest-risk work in
the whole folder.

⚠️ It ships as a PNG with **no `code.html`**, so it has to be rebuilt from the image. That is
fine — it maps directly onto the existing `organizer/events/page.tsx`.

**Direction B — Neon Glass (`og_colors`).**
Dark glassmorphism that explicitly "returns to the OG roots": violet/indigo primary, with cyan
and magenta used sparingly as accents. **Keeps Eventora's brand hue.** Typography moves entirely
to Lexend.

**Direction C — Lumina Event Horizon (`refined` ×2).**
Dark glassmorphism with **cyan as the brand primary**. Visually the most striking of the three,
and its organizer dashboard maps almost 1:1 onto your real data model (Total Bookings / Upcoming
Events / Gross Revenue, Sales Over Time with the Revenue (EGP) toggle, Recent Venues & Events
with sales-progress bars).

But swapping the brand from violet to cyan is **a rebrand, not an enhancement**. It would also
desynchronise the app from three things already shipped in violet: `site/` (the showcase site),
`site/favicon.svg`, and the 11 product screenshots in `site/assets/img/screenshots/`.

### Recommendation

Take **A now**, then **B** — not C.

B keeps the violet brand, so the app, the showcase site, and every screenshot stay coherent. C is
the better-looking pair of screens, but its cost is a brand change plus re-capturing the entire
walkthrough. If the cyan look is what you actually want, that is a legitimate choice — it just
needs to be made deliberately, as a rebrand, with the screenshot re-capture budgeted in.

---

## 3. Current-state audit (measured, not estimated)

Commands run against `frontend/src`. These numbers are the baseline any redesign has to work with.

### 3.1 The palette is in good shape

`globals.css` defines a complete **Material 3 semantic token set** — `surface`,
`surface-container-{lowest,low,high,highest}`, `on-surface`, `on-surface-variant`, `outline`,
`primary`/`on-primary`/`primary-container`, `secondary`, `tertiary`, `error`.

**The critical enabling fact:** all three Stitch `code.html` exports declare
`darkMode: "class"` and use **the exact same token names**. So a dark theme is a
*value swap under a `.dark` selector* — not a structural rewrite, not a rename, not a
component-by-component migration. That is a much cheaper path than it looks.

### 3.2 Two competing type scales are both live

| Scale | Usages |
|---|---|
| Token utilities (`font-label-sm`, `font-caption`, `font-section-heading`, …) | **173** |
| Raw Tailwind (`text-sm`, `text-xs`, `text-xl`, …) | **160** |

Nearly a 50/50 split, and they overlap:

- `text-sm` (96 uses) is 14px — the same size as `font-label-sm`, but without its 600 weight and `0.05em` tracking
- `text-xs` (32 uses) is 12px — the same size as `font-caption`

So the same visual role is expressed two different ways depending on which file you open. Any
typography change has to be applied twice, or it will land inconsistently.

### 3.3 Forty-one hardcoded hex values

Three distinct causes, each needing a different fix:

| Category | Examples | Count | Fix |
|---|---|---|---|
| **Literal duplicates of existing tokens** | `#630ed4` = primary, `#7b7487` = outline, `#e0e3e5` = surface-container-highest | ~22 | Replace with the token |
| **A genuinely missing token** | `#e6f4ea` / `#137333` success green, in 3 files | 6 | Add `--color-success` / `--color-on-success` |
| **Chart colours** | Recharts in `organizer/events/page.tsx` | ~13 | Legitimate — but read from a JS token map |

There is a `--color-error` but **no `--color-success`**. That gap is why a success green got
hardcoded in three separate files.

**Every one of these 41 values will break under a dark theme**, because they are baked to light-mode
assumptions. Fixing them is not optional cleanup — it is a hard prerequisite for Track B.

### 3.4 Other findings

- **No dark-mode support anywhere.** Zero `dark:` variants across all 23 `.tsx` files.
- **Seven `--font-*` tokens all resolve to `"Inter"`.** Pure redundancy — but convenient, since
  a Lexend swap becomes a change to the two or three that should become display faces.
- **Fonts load via `<link>` in `layout.tsx`, not `next/font`.** Render-blocking, no self-hosting,
  causes FOUT. Adding Lexend this way makes it worse; `next/font` fixes it and adds no runtime cost.
- **`.glass-panel` is a *light* glass** — `rgba(255,255,255,0.7)`. Unusable as-is in dark mode.
- **`.shimmer` is hardcoded light** (`#f6f7f8` → `#edeef1`). Same problem.
- **No radius or elevation tokens.** 4 radii and 5 shadows used ad hoc (`rounded-full` 76×,
  `rounded-xl` 65×, `shadow-md` 30×, …). Both Stitch systems define an explicit radius scale.

---

## 4. Track A — ✅ Delivered

Honoured the constraint: same palette, same typography, better execution.

- [x] **A1 · Added the missing success token.** `--color-success` / `--color-on-success` /
      `--color-success-container` / `--color-on-success-container` in `globals.css`, mirroring the
      existing `error` pair. The 6 hardcoded occurrences replaced across three files.
- [x] **A2 · Replaced token-duplicate hexes.** **41 raw hex values → 1.** The survivor is a
      `-webkit-autofill` `inset` shadow in `auth/register/page.tsx`, which needs a literal.
- [x] **A3 · Chart colours extracted** to `src/lib/chart-theme.ts`.
- [x] **A4 · Rebuilt the Sales Over Time chart** as `components/organizer/SalesChart.tsx`.
      ⚠️ The old chart was **not** a real chart — its SVG path coordinates were hardcoded, so it
      drew the same rising curve regardless of data. The replacement derives from real events,
      smooths with a cubic through each point, draws itself on mount, and tracks the pointer to
      the nearest data point. Added an explicit "No sales yet" empty state, because an honest
      flat-zero line reads as a broken chart.
      ⚠️ **Still derived, not a true daily ledger** — the organizer endpoint returns a total per
      event, not dated transactions. A genuine daily series needs a new backend endpoint.
- [x] **A5 · Restyled the status badges** — success-token fill plus a pulsing state dot.
- [x] **A6 · Sales-progress bars** — taller track, gradient fill, 1s eased width transition.
- [x] **A7 · Header + footer enrichment.** Animated underline shared by hover and active states,
      brand gradient on hover, cart icon tilt with a popped count badge, avatar fill on hover,
      dropdown scale-in, focus-within on the search field. Footer gained a brand hairline and
      links that grow a rule on hover. No invented destinations.
- [x] **A8 · Motion system** (added beyond the original plan). Reveal-on-scroll, count-up numbers,
      lift/sheen affordances, path-draw — all in `globals.css`, all disabled under
      `prefers-reduced-motion`, with `.reveal` force-shown so content can never be trapped invisible.
- [x] **A9 · Animated hero backdrop.** `HeroBackdrop` + `TicketField`: a drifting field of ticket
      glyphs over the photo. See §9 for why this is DOM rather than a video file.
- [x] **A10 · Fixed pre-existing mobile overflow.** The nav link row had no responsive handling and
      pushed the cart and account controls off-screen at 390px; text links now collapse below `md`.

**Verified:** `npx tsc --noEmit` clean · `npm run build` compiles · `npm run test` 4/4 ·
`npm run lint` **9 errors / 7 warnings — identical to the pre-existing baseline, none introduced** ·
0px horizontal overflow at 390 / 768 / 1440 · reduced-motion confirmed (ticket field hidden, every
reveal visible, counters showing final values).

---

## 5. Track B — Dark mode (additive, not a replacement)

The key framing: **add a dark theme, do not replace the light one.** That keeps the constraint
intact ("not entirely"), keeps every existing screenshot valid, and makes the whole thing
revertible with one class.

Recommended source: **Neon Glass**, because it keeps the violet brand.

- [ ] **B1 · Finish the hardcoded-hex sweep.** All 41 from §3.3. **Blocks everything else in
      Track B** — any missed literal becomes an invisible-text bug in dark mode.
- [ ] **B2 · Add the dark token block.** `.dark { … }` in `globals.css` with Neon Glass values.
      Same token names, so no component needs to change. Set `darkMode: "class"`.
- [ ] **B3 · Make `.glass-panel` theme-aware.** Currently `rgba(255,255,255,0.7)`; needs the dark
      variant `rgba(255,255,255,0.04)` + `blur(32px)` + a 10%-white border.
- [ ] **B4 · Make `.shimmer` theme-aware.** Same problem, same fix.
- [ ] **B5 · Consolidate the type scale** (§3.2). Replace the 96 `text-sm` and 32 `text-xs` with
      the token utilities. Do this **before** any font change, or the change lands half-applied.
- [ ] **B6 · Migrate fonts to `next/font`.** Removes the render-blocking `<link>`, self-hosts, kills
      FOUT. Prerequisite for adding Lexend without a further perf hit.
- [ ] **B7 · Introduce Lexend for display only.** Point `--font-hero-headline` and
      `--font-section-heading` at Lexend; leave body/label/caption on Inter. This is the *Lumina*
      pairing, and it is better than Neon Glass's Lexend-everywhere — Inter is more legible at 12–14px.
- [ ] **B8 · Add radius + elevation tokens.** Both systems specify a scale; the app currently has none.
- [ ] **B9 · Theme toggle + persistence.** Respect `prefers-color-scheme` on first visit, persist the
      explicit choice. ⚠️ Guard against the hydration flash — you already hit exactly this class of
      bug with the Zustand `userEmail` placeholder on the dashboard.
- [ ] **B10 · Apply the landing hero** per `og_colors`: gradient headline, glass search bar, ambient
      blurred colour blobs. ⚠️ Keep the **real** search form (query / city / date) — it is functional,
      not decorative.

---

## 6. Standing constraints for any screen taken from these mockups

Carried forward from the Day 22b design handoff, where these were explicit instructions:

1. **No invented routes.** All four mockups show `Features`, `Pricing`, `Documentation`, `Support`,
   `About Us`, `Career`, `Press`, `Help Center` — **none of these exist.** The footer was
   deliberately built with only real routes. Do not reintroduce them.
2. **Nav labels must match real destinations.** Mockups show `Explore / Trending / Venues /
   Schedule / Organize / Analytics`. The app has Discover, Dashboard, Organizer. Restyle the nav;
   do not invent sections.
3. **Keep the real search form.** Query + city + date, wired to `/search`.
4. **Decorative stats stay labelled as such.** `12,400+ / 2.1M / 98%` are marketing copy, not
   queries against your database — same as they are today.
5. **Real data stays real.** Featured Events pulls actual published events. Do not swap it for
   mockup placeholder cards.

---

## 7. Decisions needed before Track B starts

1. **Violet or cyan?** Neon Glass keeps the brand and the showcase site coherent. Lumina looks
   sharper but is a rebrand, and costs a re-capture of all 11 walkthrough screenshots.
2. **Dark as an option, or dark as the default?** This plan assumes *option*. Dark-only would
   invalidate every existing screenshot.
3. **Lexend everywhere, or display-only?** This plan recommends display-only (B7).
4. **Full glassmorphism, or the restrained version?** `backdrop-filter: blur(32–64px)` on many
   panels has a real cost on low-end devices and is not universally supported.

---

## 9. The hero backdrop, and how to add a real video

The hero now layers three things: a still photo with a slow ken-burns drift, a scrim for
legibility, and `TicketField` — 16 ticket glyphs falling with individual speed, scale, spin and
tint.

**Why animated DOM instead of a video file:**

1. **The CSP forbids an external one.** `next.config.ts` sets `default-src 'self'` with no
   `media-src`, so media falls back to `'self'` and any third-party video URL is blocked outright.
2. ~2 KB versus several MB, with no asset to host.
3. It re-tokens with the palette instead of baking colours into pixels.

⚠️ Every value in `TICKETS` is a fixed literal, never `Math.random()` — randomising at render time
produces different markup on server and client, which React reports as a hydration mismatch.

### To use an actual video instead

`HeroBackdrop` is already wired for it. Drop the file at:

```
frontend/public/hero.mp4      (and optionally hero.webm, preferred where supported)
```

No code change needed. The component tries the video, fades it in on `canplay`, and falls back to
the photo on error — which is exactly what happens today, since neither file exists yet (the two
404s in the console are that fallback working). It also pauses under `prefers-reduced-motion` and
catches the autoplay-rejection promise, which throws separately from `onError`.

**It must be self-hosted.** A `trycloudflare.com` or CDN URL will be CSP-blocked.

---

## 10. Suggested order

```
A1 → A2 → A3        foundation cleanup, no visual change
A4 → A5 → A6 → A7   the light enhancement you actually asked for
                    ── ship, verify, commit ──
B1 → B5             prerequisites (hex sweep, type-scale consolidation)
B2 → B3 → B4        dark tokens + theme-aware effects
B6 → B7 → B8        typography + shape system
B9 → B10            toggle, then the hero
```

Track A is independently shippable and carries no risk to the current design. Track B should not
start until A is committed, so the two are separable if the dark direction gets reconsidered.
