import { format } from "date-fns";
import { CalendarDays, MapPin } from "lucide-react";

import type { EventResponse } from "@/types/event";

type EventCardProps = {
  event: EventResponse;
  venueCity: string;
};

function estimatePrice(eventId: number): number {
  return 25 + ((eventId % 5) + 1) * 15;
}

export function EventCard({ event, venueCity }: EventCardProps) {
  const startDate = new Date(event.startDate);
  const estimatedPrice = estimatePrice(event.id);

  return (
    <article className="group overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg">
      <div className="relative h-36 bg-gradient-to-br from-emerald-400 via-cyan-400 to-blue-500">
        <div className="absolute left-3 top-3 rounded-full bg-black/70 px-3 py-1 text-xs font-medium text-white">
          {format(startDate, "MMM d")}
        </div>
      </div>

      <div className="space-y-3 p-4">
        <h3 className="line-clamp-1 text-lg font-semibold text-zinc-900" title={event.title}>
          {event.title}
        </h3>

        <div className="flex items-center gap-2 text-sm text-zinc-600">
          <CalendarDays className="h-4 w-4" />
          <span>{format(startDate, "EEEE, MMM d • p")}</span>
        </div>

        <div className="flex items-center gap-2 text-sm text-zinc-600">
          <MapPin className="h-4 w-4" />
          <span>{venueCity}</span>
        </div>

        <div className="flex items-center justify-between pt-2">
          <p className="text-sm text-zinc-500">
            From <span className="font-semibold text-zinc-900">${estimatedPrice}</span>
          </p>
          <button
            className="rounded-full bg-zinc-950 px-4 py-2 text-sm font-medium text-white transition hover:bg-zinc-700"
            type="button"
          >
            Book Now
          </button>
        </div>
      </div>
    </article>
  );
}
