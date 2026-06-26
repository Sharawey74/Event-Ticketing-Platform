/* eslint-disable @next/next/no-img-element */
"use client";

import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Calendar, MapPin, Search } from "lucide-react";
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
] as const;

type AppliedFilters = {
  query: string;
  city: string;
  date: string;
  categoryId: number | null;
};

const initialFilters: AppliedFilters = {
  query: "",
  city: "",
  date: "",
  categoryId: null,
};

export default function Home() {
  const router = useRouter();
  const token = useAuthStore((s) => s.token);
  const isLoggedIn = token !== null;
  const [draftQuery, setDraftQuery] = useState("");
  const [draftCity, setDraftCity] = useState("");
  const [draftDate, setDraftDate] = useState("");
  const [appliedFilters, setAppliedFilters] =
    useState<AppliedFilters>(initialFilters);

  const eventFilters: EventFilters = {
    q: appliedFilters.query || undefined,
    city: appliedFilters.city || undefined,
    categoryId: appliedFilters.categoryId ?? undefined,
  };

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
    queryKey: ["events", eventFilters],
    queryFn: () => fetchPublishedEvents(eventFilters),
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

  const events = eventsData?.content ?? [];

  function applySearch(): void {
    router.push(
      buildSearchHref({
        query: draftQuery,
        city: draftCity,
        date: draftDate,
        categoryId: appliedFilters.categoryId?.toString() ?? "",
      }),
    );
  }

  function toggleCategory(categoryId: number): void {
    setAppliedFilters((previous) => ({
      ...previous,
      categoryId: previous.categoryId === categoryId ? null : categoryId,
    }));
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
        </div>
      </section>

      {/* ── Category Chips ── */}
      <section className="mx-auto w-full max-w-6xl px-4 py-8 md:px-6">
        <div className="no-scrollbar flex gap-2 overflow-x-auto pb-2">
          {categories.map((category) => {
            const isActive = appliedFilters.categoryId === category.id;
            return (
              <button
                key={category.id}
                className={`rounded-full border px-4 py-2 text-sm font-medium transition-all ${
                  isActive
                    ? "border-primary bg-primary text-white shadow-md"
                    : "border-outline-variant bg-surface text-on-surface-variant hover:border-primary hover:text-primary"
                }`}
                onClick={() => toggleCategory(category.id)}
                type="button"
              >
                {category.name}
              </button>
            );
          })}
        </div>
      </section>

      {/* ── Events Grid ── */}
      <section className="mx-auto w-full max-w-6xl px-4 pb-16 md:px-6">
        <div className="mb-5 flex items-end justify-between">
          <h2 className="text-2xl font-semibold text-on-surface">
            Upcoming Events
          </h2>
          <p className="text-sm text-on-surface-variant">
            {eventsData?.totalElements ?? 0} total events
          </p>
        </div>

        {isLoading ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="h-64 rounded-2xl shimmer"
              />
            ))}
          </div>
        ) : null}

        {isError ? (
          <div className="rounded-2xl border border-outline-variant bg-surface-container-low p-8 text-center">
            <p className="text-on-surface-variant text-sm font-medium">
              Could not load events — make sure the backend is running on port
              8081.
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
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {events.map((event) => (
              <EventCard
                key={event.id}
                event={event}
                venueCity={
                  event.venueId
                    ? (venueCityById.get(event.venueId) ?? "")
                    : ""
                }
                categoryName={
                  event.categoryId
                    ? (categories.find(c => c.id === event.categoryId)?.name ?? "")
                    : ""
                }
              />
            ))}

            {events.length === 0 ? (
              <p className="col-span-full rounded-2xl border border-outline-variant bg-surface-container-low p-8 text-center text-sm text-on-surface-variant">
                No events match the current filters.
              </p>
            ) : null}
          </div>
        ) : null}
      </section>
    </div>
  );
}
