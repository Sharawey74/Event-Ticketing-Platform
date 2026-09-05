"use client";

import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Calendar, MapPin, QrCode, Search, ShieldCheck, Sparkles } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { useAuthStore } from "@/store/authStore";

import { EventCard } from "@/components/events/event-card";
import { AuroraBackdrop } from "@/components/home/AuroraBackdrop";
import { AnimatedCounter } from "@/components/ui/AnimatedCounter";
import { Reveal } from "@/components/ui/Reveal";
import { fetchCategories, fetchVenues } from "@/lib/catalog";
import { fetchPublishedEvents } from "@/lib/events";
import { buildSearchHref } from "@/lib/search";
import type { EventFilters } from "@/types/event";

const fallbackCategories = [
  { id: 1, name: "Music" },
  { id: 2, name: "Sports" },
  { id: 3, name: "Comedy" },
  { id: 4, name: "Theater" },
  { id: 5, name: "Festival" },
  { id: 6, name: "Conference" },
] as const;

const FEATURED_EVENTS_LIMIT = 6;

// Decorative marketing figures, not queries against the database — same as they
// have always been. Split into number + suffix so they can be counted up.
const HERO_HEADLINE = "Find events worth leaving the house for.";

/* Hero stats are derived from the API at render time, not authored here.
   The previous values ("12,400+ events hosted", "2.1M tickets sold",
   "98% on-time check-ins") were invented: nothing in the system counts
   lifetime tickets or check-in punctuality, so they could never become true. */

const FEATURE_STRIP = [
  {
    icon: Sparkles,
    title: "Live availability",
    description:
      "Every tier shows what is actually left, updated the instant someone else books.",
  },
  {
    icon: ShieldCheck,
    title: "Secure Stripe checkout",
    description:
      "Bank-grade encryption and Stripe-powered payments protect every transaction, every time.",
  },
  {
    icon: QrCode,
    title: "Instant QR tickets",
    description:
      "Your ticket and its QR code are issued the moment payment confirms — scan and walk in.",
  },
] as const;

const ORGANIZER_POINTS = [
  "Publish an event with tiered tickets in minutes",
  "Track tickets sold against capacity for every event you run",
  "Check attendees in at the door by scanning their QR code",
] as const;

const noFilters: EventFilters = {};

export default function Home() {
  const router = useRouter();
  const token = useAuthStore((s) => s.token);
  const isLoggedIn = token !== null;
  const [draftQuery, setDraftQuery] = useState("");
  const [draftCity, setDraftCity] = useState("");
  const [draftDate, setDraftDate] = useState("");

  const { data: categoriesData } = useQuery({
    queryKey: ["categories"],
    queryFn: fetchCategories,
  });

  const { data: venuesData } = useQuery({
    queryKey: ["venues"],
    queryFn: fetchVenues,
  });

  const {
    data: eventsData,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["events", "featured"],
    queryFn: () => fetchPublishedEvents(noFilters),
    retry: 1,
  });

  const categories =
    categoriesData?.map((item) => ({ id: item.id, name: item.name })) ??
    [...fallbackCategories];

  const venueCityById = useMemo(() => {
    const mapping = new Map<number, string>();
    venuesData?.forEach((venue) => {
      mapping.set(venue.id, venue.city);
    });
    return mapping;
  }, [venuesData]);

  const events = (eventsData?.content ?? []).slice(0, FEATURED_EVENTS_LIMIT);

  // Three counts the API can actually answer, replacing invented figures.
  // They start at zero and count up as the queries land, so nothing is
  // asserted before it is known.
  //
  // Deliberately not wrapped in useMemo: it is a map and a Set over at most 20
  // rows, and a manual memo here makes the React Compiler bail out of
  // optimising this component ("existing memoization could not be preserved").
  const allEvents = eventsData?.content ?? [];
  const heroCities = new Set(
    allEvents
      // venueId is nullable on EventResponse, so guard before the lookup.
      .map((event) =>
        event.venueId == null ? undefined : venueCityById.get(event.venueId),
      )
      .filter((city): city is string => Boolean(city)),
  );
  const heroStats = [
    {
      label: "Events on sale",
      value: eventsData?.totalElements ?? allEvents.length,
    },
    { label: "Cities", value: heroCities.size },
    { label: "Categories", value: categories.length },
  ];

  function applySearch(): void {
    router.push(
      buildSearchHref({
        query: draftQuery,
        city: draftCity,
        date: draftDate,
        categoryId: "",
      }),
    );
  }

  return (
    <div className="bg-surface">
      {/* ── Hero ── */}
      <section className="relative overflow-hidden bg-zinc-950 text-white">
        <AuroraBackdrop />

        <div className="relative mx-auto w-full max-w-6xl px-4 py-16 md:px-6 md:py-24">
          <div className="max-w-3xl space-y-4">
            <p className="animate-fade-up text-sm uppercase tracking-[0.2em] text-violet-300 font-semibold">
              Live in your city
            </p>
            {/* Word-by-word rise rather than one block fade. Split at render
                from a constant, so server and client produce identical markup. */}
            <h1 className="word-rise text-4xl font-bold leading-tight tracking-tight md:text-6xl">
              {HERO_HEADLINE.split(" ").map((word, index) => (
                <span
                  key={`${word}-${index}`}
                  style={{ "--word-index": index } as React.CSSProperties}
                >
                  {word}
                  {index < HERO_HEADLINE.split(" ").length - 1 ? " " : ""}
                </span>
              ))}
            </h1>
            <p
              className="animate-fade-up text-base text-zinc-300 md:text-lg max-w-xl"
              style={{ animationDelay: "180ms" }}
            >
              Search published events, filter by city and category, and reserve
              your seat in seconds.
            </p>

            {/* Secondary CTA glass buttons */}
            <div
              className="animate-fade-up flex flex-wrap gap-3 pt-2"
              style={{ animationDelay: "270ms" }}
            >
              <Link
                href="/search"
                className="interactive sheen inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-white/15 border border-white/30 backdrop-blur-sm text-white text-sm font-medium hover:bg-white/25 hover:border-white/50"
              >
                <Search className="h-4 w-4 transition-transform duration-300 group-hover:scale-110" />
                Browse Events
              </Link>
              <a
                href="#organizers"
                className="interactive sheen inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-white text-primary text-sm font-semibold hover:bg-white/90 hover:shadow-lg hover:shadow-primary/20"
              >
                Become an Organizer
              </a>
              {!isLoggedIn && (
                <Link
                  href="/auth/login"
                  className="interactive inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-white/10 border border-white/20 backdrop-blur-sm text-white/80 text-sm font-medium hover:bg-white/20 hover:text-white"
                >
                  Sign In
                </Link>
              )}
            </div>
          </div>

          {/* Search Bar */}
          <div
            className="animate-fade-up mt-10 grid gap-3 rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur-md transition-colors duration-300 focus-within:border-white/45 focus-within:bg-white/15 md:grid-cols-[1.3fr_1fr_1fr_auto]"
            style={{ animationDelay: "360ms" }}
          >
            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2.5 text-zinc-900">
              <Search className="h-4 w-4 shrink-0 text-zinc-400" />
              <input
                className="w-full bg-transparent text-sm outline-none placeholder:text-zinc-400"
                type="text"
                placeholder="Search title or description"
                value={draftQuery}
                onChange={(event) => setDraftQuery(event.target.value)}
              />
            </label>

            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2.5 text-zinc-900">
              <MapPin className="h-4 w-4 shrink-0 text-zinc-400" />
              <input
                className="w-full bg-transparent text-sm outline-none placeholder:text-zinc-400"
                type="text"
                placeholder="City"
                value={draftCity}
                onChange={(event) => setDraftCity(event.target.value)}
                aria-label="City"
              />
            </label>

            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2.5 text-zinc-900">
              <Calendar className="h-4 w-4 shrink-0 text-zinc-400" />
              <input
                className="w-full bg-transparent text-sm outline-none"
                type="date"
                value={draftDate}
                onChange={(event) => setDraftDate(event.target.value)}
                aria-label="Event date"
              />
            </label>

            <button
              className="interactive sheen rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white hover:bg-primary-container hover:shadow-lg hover:shadow-primary/30"
              onClick={applySearch}
              type="button"
            >
              Search Events
            </button>
          </div>

          {/* Trust stats */}
          <div
            className="animate-fade-up mt-8 flex flex-wrap gap-8 border-t border-white/15 pt-6"
            style={{ animationDelay: "450ms" }}
          >
            {heroStats.map((stat) => (
              <div key={stat.label} className="group flex flex-col gap-0.5">
                <AnimatedCounter
                  value={stat.value}
                  suffix=""
                  decimals={0}
                  className="text-2xl font-extrabold text-white transition-colors duration-300 group-hover:text-violet-300"
                />
                <span className="text-xs text-white/65">{stat.label}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Feature Strip ── */}
      <section className="mx-auto w-full max-w-6xl px-4 py-14 md:px-6 md:py-20">
        <div className="grid gap-8 sm:grid-cols-3">
          {FEATURE_STRIP.map((feature, index) => (
            <Reveal key={feature.title} delay={index * 110}>
              <div className="interactive group h-full rounded-2xl border border-transparent p-5 hover:border-outline-variant/60 hover:bg-surface-container-lowest hover:shadow-lg">
                <span className="mb-4 inline-flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary transition-all duration-300 group-hover:scale-110 group-hover:bg-primary group-hover:text-on-primary group-hover:shadow-lg group-hover:shadow-primary/30">
                  <feature.icon className="h-5 w-5" />
                </span>
                <h3 className="mb-2 text-lg font-bold text-on-surface">
                  {feature.title}
                </h3>
                <p className="text-sm leading-relaxed text-on-surface-variant">
                  {feature.description}
                </p>
              </div>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ── Featured Events ── */}
      <section className="mx-auto w-full max-w-6xl px-4 pb-16 md:px-6">
        <Reveal className="mb-6 flex items-end justify-between gap-4">
          <div className="flex items-center gap-3.5">
            {/* Brand mark sits in a tinted, glowing tile so it reads as an
                emblem rather than a stray image dropped beside the heading. */}
            <span className="relative grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-linear-to-br from-primary/15 to-secondary/10 ring-1 ring-primary/15">
              <span className="animate-pulse-glow absolute inset-0 rounded-2xl bg-primary/20 blur-md" />
              <Image
                src="/eventora-mark-v2.png"
                alt=""
                width={30}
                height={30}
                className="relative h-[30px] w-[30px]"
              />
            </span>
            <div>
              <h2 className="bg-linear-to-r from-on-surface via-primary to-secondary bg-clip-text text-2xl font-bold text-transparent">
                Featured events
              </h2>
              <p className="text-sm text-on-surface-variant">
                Handpicked and going fast this season.
              </p>
            </div>
          </div>
          <Link
            href="/search"
            className="group whitespace-nowrap text-sm font-bold text-primary transition-colors hover:text-primary-container"
          >
            See all events{" "}
            <span className="inline-block transition-transform duration-300 group-hover:translate-x-1">
              →
            </span>
          </Link>
        </Reveal>

        {isLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-64 rounded-2xl shimmer" />
            ))}
          </div>
        ) : null}

        {isError ? (
          <div className="rounded-2xl border border-outline-variant bg-surface-container-low p-8 text-center">
            <p className="text-on-surface-variant text-sm font-medium">
              Could not load events — make sure the backend is reachable at the URL below.
            </p>
            <p className="mt-1 text-xs text-outline">
              Expected:{" "}
              <code className="font-mono">
                {process.env.NEXT_PUBLIC_API_URL}/api/events
              </code>
            </p>
          </div>
        ) : null}

        {!isLoading && !isError ? (
          events.length > 0 ? (
            <div className="rail-mask -mx-4 md:-mx-6">
              <div className="scroll-rail flex gap-4 px-4 md:px-6">
              {events.map((event, index) => (
                <Reveal
                  key={event.id}
                  delay={index * 80}
                  className="w-[280px] shrink-0"
                >
                  <EventCard
                    event={event}
                    venueCity={
                      event.venueId
                        ? (venueCityById.get(event.venueId) ?? "")
                        : ""
                    }
                    categoryName={
                      event.categoryId
                        ? (categories.find((c) => c.id === event.categoryId)?.name ?? "")
                        : ""
                    }
                  />
                </Reveal>
              ))}
              </div>
            </div>
          ) : (
            <p className="rounded-2xl border border-outline-variant bg-surface-container-low p-8 text-center text-sm text-on-surface-variant">
              No published events yet — check back soon.
            </p>
          )
        ) : null}
      </section>

      {/* ── For Organizers ── */}
      <section id="organizers" className="bg-zinc-950 text-white">
        <div className="mx-auto grid w-full max-w-6xl gap-14 px-4 py-16 sm:grid-cols-2 md:px-6 md:py-24">
          <div className="flex max-w-lg flex-col gap-5">
            <span className="text-xs font-bold uppercase tracking-[0.12em] text-violet-300">
              For Organizers
            </span>
            <h2 className="text-3xl font-extrabold leading-tight tracking-tight md:text-4xl">
              Host with confidence, get paid fast.
            </h2>
            <p className="text-base leading-relaxed text-white/70">
              Set up your event page in minutes, track sales with a live
              dashboard, and receive payouts directly — no spreadsheets, no
              guesswork.
            </p>

            <ul className="mt-2 flex flex-col gap-3.5">
              {ORGANIZER_POINTS.map((point) => (
                <li key={point} className="flex items-start gap-3">
                  <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-primary" />
                  <span className="text-sm leading-relaxed text-white/85">
                    {point}
                  </span>
                </li>
              ))}
            </ul>

            <Link
              href="/auth/register"
              className="mt-3 w-fit rounded-full bg-primary px-7 py-3 text-sm font-bold text-white transition hover:bg-primary-container"
            >
              Become an Organizer
            </Link>
          </div>

          <div className="rounded-2xl border border-white/10 bg-white/5 p-6 backdrop-blur-sm">
            <div className="mb-5 flex items-center justify-between">
              <span className="text-sm font-bold">Organizer Dashboard</span>
              <div className="flex gap-1.5">
                <span className="h-1.5 w-1.5 rounded-full bg-white/40" />
                <span className="h-1.5 w-1.5 rounded-full bg-white/40" />
                <span className="h-1.5 w-1.5 rounded-full bg-white/40" />
              </div>
            </div>
            <div className="mb-5 grid grid-cols-2 gap-3.5">
              <div className="flex flex-col gap-1 rounded-xl bg-white/5 p-4">
                <span className="text-xs text-white/55">Total sales</span>
                <span className="text-xl font-extrabold">EGP 48,210</span>
                <span className="text-xs text-emerald-400">
                  +18% this week
                </span>
              </div>
              <div className="flex flex-col gap-1 rounded-xl bg-white/5 p-4">
                <span className="text-xs text-white/55">Tickets sold</span>
                <span className="text-xl font-extrabold">1,284</span>
                <span className="text-xs text-emerald-400">+9% this week</span>
              </div>
            </div>
            <div className="rounded-xl bg-white/5 p-4">
              <span className="text-xs text-white/55">Sales this week</span>
              <div className="mt-3.5 flex h-20 items-end gap-2">
                {[35, 52, 40, 70, 58, 88, 100].map((height, index) => (
                  <div
                    key={index}
                    className="flex-1 rounded-t bg-linear-to-t from-primary to-primary-container"
                    style={{ height: `${height}%` }}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
