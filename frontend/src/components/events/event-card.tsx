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

  // Map category ID to specific token backgrounds
  const categoryBadgeClasses: Record<number, string> = {
    1: "bg-secondary-fixed text-on-secondary-fixed",
    2: "bg-error-container text-on-error-container",
    3: "bg-surface-container-high text-on-surface",
    4: "bg-tertiary-fixed text-on-tertiary-fixed",
    5: "bg-primary-container text-on-primary-container",
  };
  
  const categoryClass = event.categoryId ? categoryBadgeClasses[event.categoryId] || "bg-surface-container-high text-on-surface" : "bg-surface-container-high text-on-surface";

  return (
    <article className="group overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-md transition-shadow duration-300 hover:shadow-xl cursor-pointer">
      <div className="relative h-40 bg-surface-dim">
        {/* Placeholder image representation */}
        <div className="w-full h-full bg-surface-variant flex items-center justify-center">
           <span className="text-on-surface-variant font-caption">Event Image</span>
        </div>
        
        <div className="absolute left-4 top-4 rounded-lg bg-surface/90 backdrop-blur-sm px-3 py-1.5 text-xs font-label-sm text-on-surface border border-outline-variant">
          {format(startDate, "MMM d")}
        </div>
        
        <div className={`absolute right-4 top-4 rounded-full px-3 py-1 text-xs font-label-sm border border-outline-variant ${categoryClass}`}>
          Category {event.categoryId}
        </div>
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
          <span>{venueCity}</span>
        </div>

        <div className="flex items-center justify-between pt-3 border-t border-surface-container-highest mt-2">
          <p className="font-label-sm text-on-surface-variant">
            From <span className="font-bold text-primary">${estimatedPrice}</span>
          </p>
          <button
            className="rounded-full bg-linear-to-r from-primary to-secondary px-5 py-2 text-sm font-label-sm text-on-primary transition-all hover:scale-105 hover:shadow-lg"
            type="button"
          >
            Book Now
          </button>
        </div>
      </div>
    </article>
  );
}
