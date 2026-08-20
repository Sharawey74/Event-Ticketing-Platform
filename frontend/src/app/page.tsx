"use client";

import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Calendar, MapPin, QrCode, Search, ShieldCheck, Sparkles } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { useAuthStore } from "@/store/authStore";

import { EventCard } from "@/components/events/event-card";
import { HeroBackdrop } from "@/components/home/HeroBackdrop";
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
const HERO_STATS = [
  { label: "Events hosted", value: 12400, suffix: "+", decimals: 0 },
  { label: "Tickets sold", value: 2.1, suffix: "M", decimals: 1 },
  { label: "On-time check-ins", value: 98, suffix: "%", decimals: 0 },
] as const;

const FEATURE_STRIP = [
  {
    icon: Sparkles,
    title: "Real-time seat availability",
    description:
      "See exactly what's open, down to the seat, updated the instant someone else books.",
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
      "Tickets land in your inbox the second you pay — scan and walk in, no printing required.",
  },
] as const;

const ORGANIZER_POINTS = [
  "Set up your event page in minutes, no design skills needed",
  "Real-time analytics dashboard tracks every sale as it happens",
  "Get paid out fast with automatic Stripe payouts",
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
        <HeroBackdrop />

        <div className="relative mx-auto w-full max-w-6xl px-4 py-16 md:px-6 md:py-24">
          <div className="max-w-3xl space-y-4">
            <p className="animate-fade-up text-sm uppercase tracking-[0.2em] text-violet-300 font-semibold">
              Live in your city
            </p>
            <h1
              className="animate-fade-up text-4xl font-bold tracking-tight md:text-6xl leading-tight"
              style={{ animationDelay: "90ms" }}
            >
              Find events worth leaving the house for.
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
            {HERO_STATS.map((stat) => (
              <div key={stat.label} className="group flex flex-col gap-0.5">
                <AnimatedCounter
                  value={stat.value}
                  suffix={stat.suffix}
                  decimals={stat.decimals}
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
          <div>
            <h2 className="text-2xl font-bold text-on-surface">
              Featured events
            </h2>
            <p className="text-sm text-on-surface-variant">
              Handpicked and going fast this season.
            </p>
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
            <div className="no-scrollbar -mx-4 flex gap-4 overflow-x-auto px-4 pb-2 md:-mx-6 md:px-6">
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
