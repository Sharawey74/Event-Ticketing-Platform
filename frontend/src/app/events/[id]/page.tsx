"use client";

import Link from "next/link";
import { useParams } from "next/navigation";

import { useQuery } from "@tanstack/react-query";
import { CalendarDays, MapPin, Ticket } from "lucide-react";

import { fetchVenues } from "@/lib/catalog";
import { fetchEventById } from "@/lib/event-details";

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "full",
    timeStyle: "short",
  }).format(new Date(value));
}

export default function EventDetailsPage() {
  const params = useParams<{ id: string }>();
  const eventId = Number(params.id);

  const { data: event, isLoading, isError } = useQuery({
    queryKey: ["event", eventId],
    queryFn: () => fetchEventById(eventId),
    enabled: Number.isFinite(eventId),
  });

  const { data: venues } = useQuery({
    queryKey: ["event-venues"],
    queryFn: fetchVenues,
  });

  const venueCity = venues?.find((venue) => venue.id === event?.venueId)?.city;

  if (!Number.isFinite(eventId)) {
    return (
      <div className="mx-auto w-full max-w-4xl px-4 py-10 md:px-6">
        <p className="rounded-xl border border-zinc-200 bg-white p-5 text-sm text-rose-700">
          Invalid event id.
        </p>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-10 md:px-6">
      <div className="mb-6 flex items-center justify-between">
        <Link href="/" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          Back to Home
        </Link>
        <Link href="/search" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          Search More
        </Link>
      </div>

      {isLoading ? (
        <p className="rounded-2xl border border-zinc-200 bg-white p-6 text-sm text-zinc-500">
          Loading event details...
        </p>
      ) : null}

      {isError ? (
        <p className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700">
          Could not load event details. Check the API and event id.
        </p>
      ) : null}

      {event ? (
        <article className="overflow-hidden rounded-3xl border border-zinc-200 bg-white shadow-sm">
          <div className="h-56 bg-gradient-to-br from-emerald-500 via-cyan-500 to-blue-600" />

          <div className="space-y-5 p-6 md:p-8">
            <div className="flex flex-wrap items-center gap-3 text-sm text-zinc-600">
              <span className="rounded-full bg-zinc-100 px-3 py-1 font-medium text-zinc-700">
                {event.status}
              </span>
              <span className="inline-flex items-center gap-2">
                <CalendarDays className="h-4 w-4" />
                {formatDateTime(event.startDate)}
              </span>
              <span className="inline-flex items-center gap-2">
                <MapPin className="h-4 w-4" />
                {venueCity ?? "City not available"}
              </span>
            </div>

            <div className="space-y-3">
              <h1 className="text-3xl font-bold tracking-tight text-zinc-950 md:text-4xl">
                {event.title}
              </h1>
              <p className="text-base leading-7 text-zinc-600">
                {event.description ?? "No description available for this event yet."}
              </p>
            </div>

            <div className="grid gap-4 rounded-2xl bg-zinc-50 p-5 md:grid-cols-3">
              <div>
                <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">Event ID</p>
                <p className="mt-1 text-sm font-medium text-zinc-900">#{event.id}</p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">Category</p>
                <p className="mt-1 text-sm font-medium text-zinc-900">
                  {event.categoryId ?? "Unassigned"}
                </p>
              </div>
              <div>
                <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">Venue</p>
                <p className="mt-1 text-sm font-medium text-zinc-900">
                  {event.venueId ?? "Not assigned"}
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-4 border-t border-zinc-200 pt-5">
              <div>
                <p className="text-sm text-zinc-500">Tickets are managed through booking flow later in the roadmap.</p>
              </div>
              <button
                className="inline-flex items-center gap-2 rounded-full bg-zinc-950 px-5 py-3 text-sm font-semibold text-white transition hover:bg-zinc-800"
                type="button"
              >
                <Ticket className="h-4 w-4" />
                Book Now
              </button>
            </div>
          </div>
        </article>
      ) : null}
    </div>
  );
}
