/* eslint-disable @next/next/no-img-element */
"use client";

import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Calendar, MapPin, QrCode, Search, ShieldCheck, Sparkles } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { useAuthStore } from "@/store/authStore";

import { EventCard } from "@/components/events/event-card";
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

const HERO_STATS = [
  { label: "Events hosted", value: "12,400+" },
  { label: "Tickets sold", value: "2.1M" },
  { label: "On-time check-ins", value: "98%" },
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
        {/* Concert background with purple overlay */}
        <div className="absolute inset-0">
          <img
            src="https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1600&q=80"
            alt=""
            aria-hidden="true"
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-zinc-950/70" />
          <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,rgba(99,14,212,0.65),transparent_55%),radial-gradient(ellipse_at_bottom_right,rgba(75,65,225,0.45),transparent_50%)]" />
        </div>

        <div className="relative mx-auto w-full max-w-6xl px-4 py-16 md:px-6 md:py-24">
          <div className="max-w-3xl space-y-4">
            <p className="text-sm uppercase tracking-[0.2em] text-violet-300 font-semibold">
              Live in your city
            </p>
            <h1 className="text-4xl font-bold tracking-tight md:text-6xl leading-tight">
              Find events worth leaving the house for.
            </h1>
            <p className="text-base text-zinc-300 md:text-lg max-w-xl">
              Search published events, filter by city and category, and reserve
              your seat in seconds.
            </p>

            {/* Secondary CTA glass buttons */}
            <div className="flex flex-wrap gap-3 pt-2">
              <Link
                href="/search"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-white/15 border border-white/30 backdrop-blur-sm text-white text-sm font-medium hover:bg-white/25 transition-all"
              >
                <Search className="h-4 w-4" />
                Browse Events
              </Link>
              <a
                href="#organizers"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-white text-primary text-sm font-semibold hover:bg-white/90 transition-all"
              >
                Become an Organizer
              </a>
              {!isLoggedIn && (
                <Link
                  href="/auth/login"
                  className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full bg-white/10 border border-white/20 backdrop-blur-sm text-white/80 text-sm font-medium hover:bg-white/20 transition-all"
                >
                  Sign In
                </Link>
              )}
            </div>
          </div>

          {/* Search Bar */}
          <div className="mt-10 grid gap-3 rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur-md md:grid-cols-[1.3fr_1fr_1fr_auto]">
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
              className="rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary-container hover:shadow-lg"
              onClick={applySearch}
              type="button"
            >
              Search Events
            </button>
          </div>

          {/* Trust stats */}
          <div className="mt-8 flex flex-wrap gap-8 border-t border-white/15 pt-6">
            {HERO_STATS.map((stat) => (
              <div key={stat.label} className="flex flex-col gap-0.5">
                <span className="text-2xl font-extrabold text-white">
                  {stat.value}
                </span>
                <span className="text-xs text-white/65">{stat.label}</span>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Feature Strip ── */}
      <section className="mx-auto w-full max-w-6xl px-4 py-14 md:px-6 md:py-20">
        <div className="grid gap-8 sm:grid-cols-3">
          {FEATURE_STRIP.map((feature) => (
            <div key={feature.title} className="flex flex-col gap-4">
              <span className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <feature.icon className="h-5 w-5" />
              </span>
              <h3 className="text-lg font-bold text-on-surface">
                {feature.title}
              </h3>
              <p className="text-sm leading-relaxed text-on-surface-variant">
                {feature.description}
              </p>
            </div>
          ))}
        </div>
      </section>

      {/* ── Featured Events ── */}
      <section className="mx-auto w-full max-w-6xl px-4 pb-16 md:px-6">
        <div className="mb-6 flex items-end justify-between gap-4">
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
            className="whitespace-nowrap text-sm font-bold text-primary hover:text-primary-container"
          >
            See all events →
          </Link>
        </div>

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
              {events.map((event) => (
                <div key={event.id} className="w-[280px] shrink-0">
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
                </div>
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
