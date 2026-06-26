import Link from "next/link";
import { format } from "date-fns";
import { CalendarDays, MapPin } from "lucide-react";

import type { EventResponse } from "@/types/event";

type EventCardProps = {
  event: EventResponse;
  venueCity: string;
  categoryName?: string;
};

const BADGE_PALETTE: Record<string, string> = {
  music:    "bg-purple-100 text-purple-800",
  festival: "bg-orange-100 text-orange-800",
  sports:   "bg-blue-100 text-blue-800",
  theater:  "bg-rose-100 text-rose-800",
  comedy:   "bg-yellow-100 text-yellow-800",
  tech:     "bg-teal-100 text-teal-800",
};

export function EventCard({ event, venueCity, categoryName }: EventCardProps) {
  const startDate = new Date(event.startDate);
  const displayCategory = event.categoryName ?? categoryName ?? "";
  const badgeClass =
    BADGE_PALETTE[displayCategory.toLowerCase()] ??
    "bg-surface-container-high text-on-surface-variant";

  return (
    <Link href={`/events/${event.id}`} className="group overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-md transition-shadow duration-300 hover:shadow-xl cursor-pointer block">
      <article>
        <div className="relative h-40 bg-surface-dim">
          {event.coverImageUrl ? (
            <img src={event.coverImageUrl} alt={event.title} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
          ) : (
            <div className="w-full h-full bg-surface-variant flex items-center justify-center group-hover:scale-105 transition-transform duration-500">
               <span className="text-on-surface-variant font-caption">Event Image</span>
            </div>
          )}

          <div className="absolute left-4 top-4 rounded-lg bg-surface/90 backdrop-blur-sm px-3 py-1.5 text-xs font-label-sm text-on-surface border border-outline-variant">
            {format(startDate, "MMM d")}
          </div>

          {displayCategory && (
            <div className={`absolute right-4 top-4 rounded-full px-3 py-1 text-xs font-semibold ${badgeClass}`}>
              {displayCategory}
            </div>
          )}
        </div>

        <div className="space-y-3 p-5">
          <h3 className="line-clamp-1 font-section-heading text-lg text-on-surface group-hover:text-primary transition-colors" title={event.title}>
            {event.title}
          </h3>

          <div className="flex items-center gap-2 text-sm text-on-surface-variant font-body">
            <CalendarDays className="h-4 w-4 text-outline" />
            <span>{format(startDate, "EEEE, MMM d • p")}</span>
          </div>

          <div className="flex items-center gap-2 text-sm text-on-surface-variant font-body">
            <MapPin className="h-4 w-4 text-outline" />
            <span>{venueCity || "TBA"}</span>
          </div>

          <div className="flex items-center justify-between pt-3 border-t border-surface-container-highest mt-2">
            <p className="font-label-sm text-on-surface-variant">
              {event.minPrice != null
                ? <>From <span className="font-bold text-primary">EGP {Number(event.minPrice).toLocaleString()}</span></>
                : <span className="text-on-surface-variant">Price TBA</span>
              }
            </p>
            <span
              className="inline-block rounded-full bg-linear-to-r from-primary to-secondary px-5 py-2 text-sm font-label-sm text-on-primary transition-all hover:scale-105 hover:shadow-lg"
            >
              Book Now
            </span>
          </div>
        </div>
      </article>
    </Link>
  );
}
