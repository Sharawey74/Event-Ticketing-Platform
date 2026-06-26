"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import { MapPin, Calendar } from "lucide-react";
import type { EventResponse } from "@/types/event";

interface SearchDropdownProps {
  query: string;
  cities: string[];
  events: EventResponse[];
  onClose: () => void;
  onSelectCity: (city: string) => void;
}

function formatShortDate(iso: string): string {
  return new Intl.DateTimeFormat("en-EG", {
    month: "short",
    day: "numeric",
  }).format(new Date(iso));
}

export function SearchDropdown({
  query,
  cities,
  events,
  onClose,
  onSelectCity,
}: SearchDropdownProps) {
  const ref = useRef<HTMLDivElement>(null);

  // Dismiss on click outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [onClose]);

  // Dismiss on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  const hasCities = cities.length > 0;
  const hasEvents = events.length > 0;

  if (!hasCities && !hasEvents) return null;

  return (
    <div
      ref={ref}
      className="absolute top-full left-0 right-0 mt-2 bg-surface-container-lowest border border-outline-variant/40 rounded-xl shadow-2xl overflow-hidden z-40"
    >
      {hasCities && (
        <div>
          <p className="px-4 pt-3 pb-1 font-caption text-caption text-on-surface-variant uppercase tracking-wider">
            Locations
          </p>
          {cities.map((city) => (
            <button
              key={city}
              type="button"
              onClick={() => {
                onSelectCity(city);
                onClose();
              }}
              className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-surface-container-low transition-colors text-left"
            >
              <MapPin className="h-4 w-4 text-primary shrink-0" />
              <span className="font-body text-body text-on-surface">
                {city}
              </span>
              <span className="font-caption text-caption text-on-surface-variant ml-auto">
                Egypt
              </span>
            </button>
          ))}
        </div>
      )}

      {hasCities && hasEvents && (
        <div className="border-t border-outline-variant/30" />
      )}

      {hasEvents && (
        <div>
          <p className="px-4 pt-3 pb-1 font-caption text-caption text-on-surface-variant uppercase tracking-wider">
            Events
          </p>
          {events.map((event) => (
            <Link
              key={event.id}
              href={`/events/${event.id}`}
              onClick={onClose}
              className="flex items-start gap-3 px-4 py-2.5 hover:bg-surface-container-low transition-colors"
            >
              <Calendar className="h-4 w-4 text-secondary shrink-0 mt-0.5" />
              <div className="min-w-0">
                <p className="font-body text-body text-on-surface truncate">{event.title}</p>
                <p className="font-caption text-caption text-on-surface-variant">
                  {formatShortDate(event.startDate)}
                </p>
              </div>
            </Link>
          ))}
        </div>
      )}

      <div className="px-4 py-2 border-t border-outline-variant/30">
        <p className="font-caption text-caption text-on-surface-variant text-center">
          Press Enter to search all results for &quot;{query}&quot;
        </p>
      </div>
    </div>
  );
}
