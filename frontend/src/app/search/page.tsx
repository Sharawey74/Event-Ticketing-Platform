"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";

import { EventCard } from "@/components/events/event-card";
import { fetchCategories, fetchVenues } from "@/lib/catalog";
import { fetchPublishedEvents } from "@/lib/events";
import { buildSearchHref } from "@/lib/search";
import type { EventFilters } from "@/types/event";

const defaultFilters = {
  q: "",
  city: "",
  date: "",
  categoryId: "",
};

export default function SearchPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto w-full max-w-6xl px-4 py-10 md:px-6">
          <p className="rounded-xl border border-zinc-200 bg-white p-5 text-sm text-zinc-500">
            Loading search page...
          </p>
        </div>
      }
    >
      <SearchPageContent />
    </Suspense>
  );
}

function SearchPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialFilters = useMemo(
    () => ({
      q: searchParams.get("q") ?? defaultFilters.q,
      city: searchParams.get("city") ?? defaultFilters.city,
      date: searchParams.get("date") ?? defaultFilters.date,
      categoryId: searchParams.get("categoryId") ?? defaultFilters.categoryId,
    }),
    [searchParams],
  );

  const [query, setQuery] = useState(initialFilters.q);
  const [city, setCity] = useState(initialFilters.city);
  const [date, setDate] = useState(initialFilters.date);
  const [categoryId, setCategoryId] = useState(initialFilters.categoryId);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setQuery(initialFilters.q);
    setCity(initialFilters.city);
    setDate(initialFilters.date);
    setCategoryId(initialFilters.categoryId);
  }, [initialFilters]);

  const filters: EventFilters = {
    q: query.trim() || undefined,
    city: city.trim() || undefined,
    categoryId: categoryId ? Number(categoryId) : undefined,
  };

  const { data: categoriesData } = useQuery({
    queryKey: ["search-categories"],
    queryFn: fetchCategories,
  });

  const { data: venuesData } = useQuery({
    queryKey: ["search-venues"],
    queryFn: fetchVenues,
  });

  const {
    data: eventsData,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ["search-events", filters],
    queryFn: () => fetchPublishedEvents(filters),
  });

  const filteredEvents = useMemo(() => {
    const events = eventsData?.content ?? [];

    if (!date) {
      return events;
    }

    return events.filter((event) => event.startDate.startsWith(date));
  }, [eventsData, date]);

  const venueCityById = useMemo(() => {
    const mapping = new Map<number, string>();

    venuesData?.forEach((venue) => {
      mapping.set(venue.id, venue.city);
    });

    return mapping;
  }, [venuesData]);

  const resultCount = filteredEvents.length;

  function applyFilters(): void {
    router.push(
      buildSearchHref({
        query,
        city,
        date,
        categoryId,
      }),
    );
  }

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-10 md:px-6">
      <div className="mb-8 space-y-2">
        <p className="text-sm uppercase tracking-[0.16em] text-emerald-700">Search</p>
        <h1 className="text-3xl font-bold tracking-tight text-zinc-950">Event Results</h1>
        <p className="text-sm text-zinc-600">Refine by text, city, category, and date.</p>
      </div>

      <section className="mb-8 grid gap-3 rounded-2xl border border-zinc-200 bg-white p-4 md:grid-cols-4">
        <input
          className="rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search title or description"
        />

        <input
          className="rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
          value={city}
          onChange={(event) => setCity(event.target.value)}
          placeholder="City"
          aria-label="City"
        />

        <select
          className="rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
          value={categoryId}
          onChange={(event) => setCategoryId(event.target.value)}
          aria-label="Event category"
        >
          <option value="">All Categories</option>
          {categoriesData?.map((category) => (
            <option key={category.id} value={category.id.toString()}>
              {category.name}
            </option>
          ))}
        </select>

        <input
          className="rounded-lg border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
          aria-label="Event date"
        />

        <button
          className="rounded-lg bg-zinc-950 px-4 py-2 text-sm font-medium text-white transition hover:bg-zinc-800 md:col-span-4"
          type="button"
          onClick={applyFilters}
        >
          Apply Filters
        </button>
      </section>

      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-zinc-600">{resultCount} events found</p>
        <Link href="/" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          Back to Home
        </Link>
      </div>

      {isLoading ? (
        <p className="rounded-xl border border-zinc-200 bg-white p-5 text-sm text-zinc-500">
          Loading search results...
        </p>
      ) : null}

      {isError ? (
        <p className="rounded-xl border border-rose-200 bg-rose-50 p-5 text-sm text-rose-700">
          Could not load search results. Verify API URL and backend availability.
        </p>
      ) : null}

      {!isLoading && !isError ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filteredEvents.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              venueCity={
                (event.venueId && venueCityById.get(event.venueId)) ||
                "City not available"
              }
            />
          ))}

          {filteredEvents.length === 0 ? (
            <p className="col-span-full rounded-xl border border-zinc-200 bg-white p-5 text-sm text-zinc-600">
              No events matched your filters.
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
