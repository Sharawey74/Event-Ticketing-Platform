"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { SearchX } from "lucide-react";

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
          <p className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 text-sm text-on-surface-variant">
            Loading search…
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

  // Navigating to the bare route is enough: the effect above re-seeds every
  // field from the URL, so the local state and the address bar cannot drift.
  function clearFilters(): void {
    router.push("/search");
  }

  // Echo the filters that produced an empty result, so "nothing found" names
  // what was actually searched instead of leaving the user to guess.
  const activeFilterSummary = [
    query.trim() ? `“${query.trim()}”` : null,
    city.trim() ? `in ${city.trim()}` : null,
    categoryId
      ? categoriesData?.find((c) => c.id.toString() === categoryId)?.name
      : null,
    date || null,
  ]
    .filter(Boolean)
    .join(" · ");

  const inputClass =
    "min-h-11 rounded-lg border border-outline bg-surface-container-lowest px-3 text-sm text-on-surface outline-none transition-colors placeholder:text-outline-text focus:border-primary focus:ring-2 focus:ring-primary/25";

  return (
    <div className="mx-auto w-full max-w-6xl px-4 py-10 md:px-6">
      <div className="mb-8 space-y-2">
        <p className="font-label-sm uppercase tracking-[0.16em] text-primary">Search</p>
        <h1 className="text-3xl font-bold tracking-tight text-on-surface">Event results</h1>
        <p className="text-sm text-on-surface-variant">
          Refine by text, city, category, and date.
        </p>
      </div>

      <section className="mb-8 grid gap-3 rounded-2xl border border-outline-variant bg-surface-container-lowest p-4 md:grid-cols-4">
        <label className="sr-only" htmlFor="search-query">
          Search title or description
        </label>
        <input
          id="search-query"
          className={inputClass}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search title or description"
        />

        <label className="sr-only" htmlFor="search-city">
          City
        </label>
        <input
          id="search-city"
          className={inputClass}
          value={city}
          onChange={(event) => setCity(event.target.value)}
          placeholder="City"
        />

        <label className="sr-only" htmlFor="search-category">
          Event category
        </label>
        <select
          id="search-category"
          className={inputClass}
          value={categoryId}
          onChange={(event) => setCategoryId(event.target.value)}
        >
          <option value="">All categories</option>
          {categoriesData?.map((category) => (
            <option key={category.id} value={category.id.toString()}>
              {category.name}
            </option>
          ))}
        </select>

        <label className="sr-only" htmlFor="search-date">
          Event date
        </label>
        <input
          id="search-date"
          className={inputClass}
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />

        <button
          className="interactive min-h-11 rounded-lg bg-primary px-4 text-sm font-semibold text-on-primary outline-none transition-colors hover:bg-primary-container focus-visible:ring-2 focus-visible:ring-primary/50 md:col-span-4"
          type="button"
          onClick={applyFilters}
        >
          Apply filters
        </button>
      </section>

      <div className="mb-4 flex items-center justify-between gap-4">
        <p className="text-sm text-on-surface-variant">
          {isLoading ? "Searching…" : `${resultCount} ${resultCount === 1 ? "event" : "events"} found`}
        </p>
        <Link
          href="/"
          className="link-underline text-sm font-medium text-primary hover:text-primary-container"
        >
          Back to home
        </Link>
      </div>

      {isLoading ? (
        // Skeleton cards rather than a line of text: this reserves the space the
        // results will occupy, so the page does not jump when they land.
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, index) => (
            <div
              key={index}
              className="h-72 animate-pulse rounded-xl border border-outline-variant bg-surface-container-low"
            />
          ))}
        </div>
      ) : null}

      {isError ? (
        <div
          role="alert"
          className="rounded-xl border border-error/30 bg-error-container p-5 text-sm text-on-error-container"
        >
          <p className="font-semibold">Could not load search results.</p>
          <p className="mt-1">
            The backend did not respond. Check that it is running and that
            NEXT_PUBLIC_API_URL points at it.
          </p>
        </div>
      ) : null}

      {!isLoading && !isError ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filteredEvents.map((event) => (
            <EventCard
              key={event.id}
              event={event}
              venueCity={
                event.venueId
                  ? venueCityById.get(event.venueId) ?? "City not available"
                  : "City not available"
              }
            />
          ))}

          {filteredEvents.length === 0 ? (
            <div className="col-span-full flex flex-col items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest px-6 py-14 text-center">
              <SearchX className="h-8 w-8 text-outline" aria-hidden="true" />
              <p className="font-semibold text-on-surface">
                {activeFilterSummary
                  ? `No events match ${activeFilterSummary}`
                  : "No events match your filters"}
              </p>
              <p className="max-w-sm text-sm text-on-surface-variant">
                Try a different city or category, or clear the filters to see
                everything on sale.
              </p>
              <button
                type="button"
                onClick={clearFilters}
                className="interactive mt-1 inline-flex min-h-11 items-center rounded-full border border-outline-variant px-5 text-sm font-semibold text-primary outline-none transition-colors hover:bg-primary/10 focus-visible:ring-2 focus-visible:ring-primary/50"
              >
                Clear filters
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
