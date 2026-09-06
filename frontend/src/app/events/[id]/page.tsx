import Link from "next/link";
import { TicketTierSelector } from "@/components/events/TicketTierSelector";
import { EventDetailResponse } from "@/types/event";
import { CalendarDays, ChevronRight, MapPin } from "lucide-react";

async function getEventDetails(id: string): Promise<EventDetailResponse | null> {
  const url = `${process.env.NEXT_PUBLIC_API_URL}/api/events/${id}`;
  try {
    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) {
      if (res.status === 404) return null;
      throw new Error("Failed to fetch event details");
    }
    const json = await res.json();
    return json.data;
  } catch (error) {
    console.error("Error fetching event details:", error);
    return null;
  }
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

export default async function EventDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const event = await getEventDetails(id);

  if (!event) {
    return (
      <div className="mx-auto w-full max-w-4xl px-4 py-20 text-center">
        <h1 className="text-3xl font-bold text-error">Event Not Found</h1>
        <p className="mt-4 text-on-surface-variant">The event you are looking for does not exist or has been removed.</p>
        <Link href="/" className="mt-6 inline-block text-primary hover:underline">
          Return to Home
        </Link>
      </div>
    );
  }

  return (
    <div className="pb-section-gap">
      {/* Cover Image */}
      <div className="w-full h-[409px] md:h-[512px] relative overflow-hidden bg-surface-container-high">
        {event.coverImageUrl ? (
          <img
            alt={event.title}
            src={event.coverImageUrl}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full bg-gradient-to-br from-primary to-secondary" />
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-background to-transparent"></div>
      </div>

      <div className="max-w-container-max mx-auto px-edge-padding -mt-32 relative z-10">
        {/* Breadcrumbs */}
        <nav aria-label="Breadcrumb" className="flex text-on-surface-variant font-caption text-caption mb-stack-md">
          <ol className="inline-flex items-center space-x-1 md:space-x-3">
            <li className="inline-flex items-center">
              <Link className="hover:text-primary transition-colors" href="/events">
                Events
              </Link>
            </li>
            <li>
              <div className="flex items-center">
                <ChevronRight className="h-[16px] w-[16px] mx-1" aria-hidden="true" />
                <Link className="hover:text-primary transition-colors" href={`/search?categoryId=${event.category?.id || ""}`}>
                  {event.category?.name || "Category"}
                </Link>
              </div>
            </li>
            <li aria-current="page">
              <div className="flex items-center">
                <ChevronRight className="h-[16px] w-[16px] mx-1" aria-hidden="true" />
                <span className="text-primary font-medium">{event.title}</span>
              </div>
            </li>
            {event.venue ? (
              <div className="flex flex-col gap-1">
                <span className="font-medium text-on-surface">{event.venue.name}</span>
                <span className="text-on-surface-variant text-sm">{event.venue.address}, {event.venue.city}</span>
              </div>
            ) : null}
          </ol>
        </nav>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-gutter">
          {/* Left Column: Event Details */}
          <div className="lg:col-span-7 xl:col-span-8 flex flex-col gap-stack-lg">
            {/* Title & Badges */}
            <div>
              <div className="flex flex-wrap gap-2 mb-stack-sm">
                <span className="px-4 py-1 bg-primary/10 text-primary rounded-full font-label-sm text-label-sm">
                  {event.category?.name || "Event"}
                </span>
                <span className="px-4 py-1 bg-primary/10 text-primary rounded-full font-label-sm text-label-sm">
                  {event.status}
                </span>
              </div>
              <h1 className="font-hero-headline text-hero-headline text-on-surface mb-stack-sm">
                {event.title}
              </h1>
              <p className="font-body-lg text-body-lg text-on-surface-variant">
                {event.description || "No description available for this event yet."}
              </p>
            </div>

            {/* Meta Info Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-stack-md bg-surface-container-lowest p-6 rounded-xl shadow-md border border-outline-variant/10">
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                  <CalendarDays className="h-4 w-4 text-primary" aria-hidden="true" />
                </div>
                <div>
                  <h2 className="font-label-sm text-label-sm text-on-surface mb-1">Date & Time</h2>
                  <p className="font-body text-body text-on-surface-variant">
                    {formatDateTime(event.startDate)}
                    <br />
                    to {formatDateTime(event.endDate)}
                  </p>
                </div>
              </div>
              
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                  <MapPin className="h-4 w-4 text-primary" aria-hidden="true" />
                </div>
                <div>
                  <h2 className="font-label-sm text-label-sm text-on-surface mb-1">Venue</h2>
                  <p className="font-body text-body text-on-surface-variant">
                    {event.venue?.name || "TBA"}
                    <br />
                    {event.venue?.city || ""} {event.venue?.country || ""}
                  </p>
                </div>
              </div>
            </div>

            {/* Description */}
            <div className="bg-surface-container-lowest p-6 rounded-xl shadow-md border border-outline-variant/10">
              <h2 className="font-section-heading text-section-heading text-on-surface mb-stack-md">About This Event</h2>
              <div className="font-body text-body text-on-surface-variant space-y-4 whitespace-pre-wrap">
                {event.description || "No description available for this event yet."}
              </div>
              
              <div className="mt-8 pt-6 border-t border-outline-variant/20">
                <h3 className="font-label-sm text-label-sm text-on-surface mb-2">Organizer</h3>
                <p className="font-body text-body text-on-surface-variant">
                  {event.organizer?.name || "Unknown Organizer"}
                </p>
              </div>
            </div>
          </div>

          {/* Right Column: Sticky Ticketing */}
          <div className="lg:col-span-5 xl:col-span-4 relative">
            <TicketTierSelector eventId={event.id} eventTitle={event.title} tiers={event.ticketTiers || []} />
          </div>
        </div>
      </div>
    </div>
  );
}
