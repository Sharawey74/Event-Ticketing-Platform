"use client";

import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { Calendar, MapPin, Search } from "lucide-react";
import { useRouter } from "next/navigation";

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
  const [draftQuery, setDraftQuery] = useState("");
  const [draftCity, setDraftCity] = useState("");
  const [draftDate, setDraftDate] = useState("");
  const [appliedFilters, setAppliedFilters] = useState<AppliedFilters>(
    initialFilters,
  );

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
    <div className="bg-zinc-50">
      <section className="relative overflow-hidden bg-zinc-950 text-white">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,#22c55e,transparent_45%),radial-gradient(circle_at_bottom_right,#0ea5e9,transparent_35%)] opacity-80" />
        <div className="relative mx-auto w-full max-w-6xl px-4 py-16 md:px-6 md:py-20">
          <div className="max-w-3xl space-y-4">
            <p className="text-sm uppercase tracking-[0.2em] text-emerald-300">
              Live in your city
            </p>
            <h1 className="text-4xl font-bold tracking-tight md:text-6xl">
              Find events worth leaving the house for.
            </h1>
            <p className="text-base text-zinc-200 md:text-lg">
              Search published events, filter by city and category, and reserve
              your seat in seconds.
            </p>
          </div>

          <div className="mt-8 grid gap-3 rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur-md md:grid-cols-[1.3fr_1fr_1fr_auto]">
            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2 text-zinc-900">
              <Search className="h-4 w-4 text-zinc-500" />
              <input
                className="w-full bg-transparent text-sm outline-none"
                type="text"
                placeholder="Search title or description"
                value={draftQuery}
                onChange={(event) => setDraftQuery(event.target.value)}
              />
            </label>

            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2 text-zinc-900">
              <MapPin className="h-4 w-4 text-zinc-500" />
              <input
                className="w-full bg-transparent text-sm outline-none"
                type="text"
                placeholder="City"
                value={draftCity}
                onChange={(event) => setDraftCity(event.target.value)}
                aria-label="City"
              />
            </label>

            <label className="flex items-center gap-2 rounded-xl bg-white px-3 py-2 text-zinc-900">
              <Calendar className="h-4 w-4 text-zinc-500" />
              <input
                className="w-full bg-transparent text-sm outline-none"
                type="date"
                value={draftDate}
                onChange={(event) => setDraftDate(event.target.value)}
                aria-label="Event date"
              />
            </label>

            <button
              className="rounded-xl bg-emerald-500 px-5 py-2.5 text-sm font-semibold text-emerald-950 transition hover:bg-emerald-400"
              onClick={applySearch}
              type="button"
            >
              Search Events
            </button>
          </div>
        </div>
      </section>

      <section className="mx-auto w-full max-w-6xl px-4 py-8 md:px-6">
        <div className="no-scrollbar flex gap-2 overflow-x-auto pb-2">
          {categories.map((category) => {
            const isActive = appliedFilters.categoryId === category.id;

            return (
              <button
                key={category.id}
                className={`rounded-full border px-4 py-2 text-sm font-medium transition ${
                  isActive
                    ? "border-emerald-600 bg-emerald-600 text-white"
                    : "border-zinc-300 bg-white text-zinc-700 hover:border-zinc-950"
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

      <section className="mx-auto w-full max-w-6xl px-4 pb-16 md:px-6">
        <div className="mb-5 flex items-end justify-between">
          <h2 className="text-2xl font-semibold text-zinc-950">Upcoming Events</h2>
          <p className="text-sm text-zinc-500">
            {eventsData?.totalElements ?? 0} total events
          </p>
        </div>

        {isLoading ? (
          <p className="rounded-2xl border border-zinc-200 bg-white p-6 text-sm text-zinc-500">
            Loading events...
          </p>
        ) : null}

        {isError ? (
          <p className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700">
            Failed to load events. Check NEXT_PUBLIC_API_URL and backend status.
          </p>
        ) : null}

        {!isLoading && !isError ? (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {events.map((event) => (
              <EventCard
                key={event.id}
                event={event}
                venueCity={
                  (event.venueId && venueCityById.get(event.venueId)) ||
                  "City not available"
                }
              />
            ))}

            {events.length === 0 ? (
              <p className="col-span-full rounded-2xl border border-zinc-200 bg-white p-6 text-sm text-zinc-600">
                No events match the current filters.
              </p>
            ) : null}
          </div>
        ) : null}
      </section>
    </div>
  );
}
