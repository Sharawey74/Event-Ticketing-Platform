/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";
import { SalesChart } from "@/components/organizer/SalesChart";
import { AnimatedCounter } from "@/components/ui/AnimatedCounter";
import { Reveal } from "@/components/ui/Reveal";

interface OrganizerEvent {
  id: number;
  title: string;
  date: string;
  status: "ACTIVE" | "DRAFT";
  sold: number;
  capacity: number;
  grossRevenue: number;
  thumbnailUrl?: string;
}

export default function OrganizerDashboardPage() {
  const router = useRouter();
  const { token, userRole } = useAuthStore();
  const [isClient, setIsClient] = useState(false);
  const [mountedAt, setMountedAt] = useState(0);

  const { data: eventsData, isLoading } = useQuery({
    queryKey: ["organizerEvents"],
    queryFn: async () => {
      const res = await api.get("/api/v1/organizer/events");
      return res.data?.data || [];
    },
    enabled: isClient && !!token && userRole === "ORGANIZER",
  });

  const events = useMemo(() => (eventsData as OrganizerEvent[]) || [], [eventsData]);

  // Derived from the events the API already returns — no invented figures.
  // `now` is captured once on mount rather than read during render: Date.now()
  // is impure, so calling it here would make the memo non-deterministic.
  const summary = useMemo(() => {
    const now = mountedAt;
    return {
      totalBookings: events.reduce((sum, e) => sum + (e.sold || 0), 0),
      upcoming: events.filter((e) => new Date(e.date).getTime() > now).length,
      grossRevenue: events.reduce((sum, e) => sum + (e.grossRevenue || 0), 0),
      nextEvent: events
        .filter((e) => new Date(e.date).getTime() > now)
        .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())[0],
    };
  }, [events, mountedAt]);

  // Mark client ready after first render so Zustand can finish rehydrating from localStorage
  useEffect(() => { setIsClient(true); setMountedAt(Date.now()); }, []);

  useEffect(() => {
    if (!isClient) return;
    if (!token) {
      router.push("/auth/login");
      return;
    }
    if (userRole !== "ORGANIZER") {
      router.push("/dashboard/bookings");
      return;
    }
  }, [isClient, token, userRole, router]);

  if (!isClient) {
    return <div className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen flex items-center justify-center"><p>Loading dashboard...</p></div>;
  }
  // Redirect in flight — render nothing to avoid a flash of empty content
  if (!token || userRole !== "ORGANIZER") {
    return null;
  }
  if (isLoading) {
    return <div className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen flex items-center justify-center"><p>Loading dashboard...</p></div>;
  }

  return (
    <div className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen">
      
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-stack-md mb-stack-lg">
        <div>
          <h1 className="font-hero-headline text-hero-headline text-on-surface">My Events</h1>
          <p className="font-body text-body text-on-surface-variant mt-2">Manage your upcoming venues and track performance.</p>
        </div>
        <Link
          href="/organizer/events/new"
          className="interactive sheen group bg-primary text-on-primary rounded-full px-6 py-3 shadow-md hover:shadow-xl hover:shadow-primary/30 flex items-center justify-center gap-2 font-label-sm w-fit"
        >
          <span className="material-symbols-outlined text-[20px] transition-transform duration-300 group-hover:rotate-90">add</span>
          Create New Event
        </Link>
      </div>

      {/* Summary Cards — all three derived from the events list, not invented */}
      <div className="grid gap-stack-md sm:grid-cols-2 lg:grid-cols-3 mb-stack-lg">
        {[
          {
            label: "Total Bookings",
            icon: "confirmation_number",
            node: <AnimatedCounter value={summary.totalBookings} />,
            sub: `${events.length} event${events.length === 1 ? "" : "s"} total`,
          },
          {
            label: "Upcoming Events",
            icon: "event_upcoming",
            node: <AnimatedCounter value={summary.upcoming} />,
            sub: summary.nextEvent ? `Next: ${summary.nextEvent.title}` : "None scheduled",
          },
          {
            label: "Gross Revenue",
            icon: "payments",
            node: <AnimatedCounter value={summary.grossRevenue} prefix="EGP " decimals={2} />,
            sub: "Across all events",
          },
        ].map((card, i) => (
          <Reveal key={card.label} delay={i * 90}>
            <div className="interactive group bg-surface-container-lowest rounded-xl shadow-md p-stack-md border border-outline-variant/30 hover:shadow-xl hover:border-primary/40 h-full">
              <div className="flex items-start justify-between gap-3 mb-3">
                <span className="font-label-sm text-on-surface-variant uppercase">{card.label}</span>
                <span className="w-9 h-9 rounded-lg bg-primary/10 text-primary flex items-center justify-center shrink-0 transition-all duration-300 group-hover:bg-primary group-hover:text-on-primary group-hover:scale-110">
                  <span className="material-symbols-outlined text-[20px]">{card.icon}</span>
                </span>
              </div>
              <p className="font-hero-headline text-[32px] leading-tight text-on-surface">{card.node}</p>
              <p className="font-caption text-on-surface-variant mt-1 line-clamp-1">{card.sub}</p>
            </div>
          </Reveal>
        ))}
      </div>

      {/* Sales Chart Card */}
      <div className="bg-surface-container-lowest rounded-xl shadow-md p-stack-lg mb-stack-lg border border-outline-variant/30 transition-shadow hover:shadow-xl">
        {/* "Sales Over Time" and a "Last 30 Days" picker both promised a time
            dimension the API does not have — and the picker was inert, with no
            handler behind it. A control that does nothing is worse than no
            control: it implies the data could be filtered. */}
        <div className="mb-stack-lg flex flex-wrap items-center gap-3">
          <h2 className="font-section-heading text-section-heading text-on-surface">
            Revenue by event
          </h2>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-surface-container-low px-2.5 py-1">
            <span className="h-2 w-2 rounded-full bg-primary" />
            <span className="font-caption text-on-surface-variant">Gross (EGP)</span>
          </span>
        </div>
        
        <SalesChart events={events} />
      </div>

      {/* Events List Section */}
      <div>
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-stack-md">
          <h2 className="font-section-heading text-section-heading text-on-surface">Recent Venues & Events</h2>
          <div className="relative w-full sm:w-64">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]">search</span>
            <input 
              type="text" 
              placeholder="Search events..." 
              className="w-full rounded-full border border-outline-variant py-2 pl-10 pr-4 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body text-sm"
            />
          </div>
        </div>

        <div className="space-y-stack-md">
          {events.length > 0 ? (
            events.map((event, index) => (
              <Reveal key={event.id} delay={index * 70}>
              <div className="interactive group bg-surface-container-lowest rounded-xl shadow-md p-stack-md border border-outline-variant/30 flex flex-col lg:flex-row lg:items-center gap-stack-md hover:shadow-xl hover:border-primary/40 cursor-pointer" onClick={() => router.push(`/organizer/events/${event.id}/attendees`)}>

                {/* Part 1 - Event Info */}
                <div className="flex-1 flex items-center gap-4 min-w-[300px]">
                  <div className="w-32 h-20 rounded-lg overflow-hidden bg-surface-container-high shrink-0">
                    {event.thumbnailUrl ? (
                      <img src={event.thumbnailUrl} alt={event.title} className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-110" />
                    ) : (
                      <div className="w-full h-full bg-linear-to-br from-primary-container to-secondary-container transition-transform duration-500 group-hover:scale-110" />
                    )}
                  </div>
                  <div>
                    <h3 className="font-body-lg font-bold text-on-surface line-clamp-1 transition-colors group-hover:text-primary">{event.title}</h3>
                    <p className="font-caption text-on-surface-variant mb-2">
                      {new Date(event.date).toLocaleDateString()}
                    </p>
                    <span
                      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold tracking-wide uppercase transition-colors ${
                        event.status === "ACTIVE"
                          ? "bg-success-container text-on-success-container"
                          : "bg-surface-container-high text-on-surface-variant"
                      }`}
                    >
                      <span
                        className={`w-1.5 h-1.5 rounded-full ${
                          event.status === "ACTIVE" ? "bg-success animate-pulse" : "bg-outline"
                        }`}
                      />
                      {event.status}
                    </span>
                  </div>
                </div>

                {/* Part 2 - Sales Progress */}
                <div className="flex-1 lg:border-l border-outline-variant/30 lg:pl-stack-md flex flex-col justify-center py-2 lg:py-0">
                  <div className="flex justify-between items-end mb-2">
                    <span className="font-caption text-on-surface-variant">Sales Progress</span>
                    <span className="font-label-sm text-on-surface">{event.sold} <span className="font-caption text-on-surface-variant font-normal">/ {event.capacity}</span></span>
                  </div>
                  <div className="w-full bg-surface-container-high rounded-full h-2.5 overflow-hidden">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-primary to-secondary transition-[width] duration-1000 ease-out"
                      style={{
                        width: `${Math.min(100, Math.max(0, (event.sold / event.capacity) * 100))}%`,
                      }}
                    />
                  </div>
                </div>

                {/* Part 3 - Revenue + Actions */}
                <div className="flex-1 lg:border-l border-outline-variant/30 lg:pl-stack-md flex items-center justify-between py-2 lg:py-0">
                  <div>
                    <p className="font-caption text-on-surface-variant mb-1">Gross Revenue</p>
                    <p className="font-label-sm text-body-lg font-bold text-on-surface">EGP {(event.grossRevenue || 0).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      className="interactive w-10 h-10 rounded-full border border-outline-variant hover:text-on-primary hover:border-primary hover:bg-primary flex items-center justify-center text-on-surface-variant"
                      title="View Attendees"
                      onClick={(e) => { e.stopPropagation(); router.push(`/organizer/events/${event.id}/attendees`); }}
                    >
                      <span className="material-symbols-outlined text-[20px]">group</span>
                    </button>
                    <button
                      className="interactive w-10 h-10 rounded-full border border-outline-variant hover:text-on-primary hover:border-primary hover:bg-primary flex items-center justify-center text-on-surface-variant"
                      title="Edit Event"
                      onClick={(e) => { e.stopPropagation(); router.push(`/organizer/events/${event.id}/edit`); }}
                    >
                      <span className="material-symbols-outlined text-[20px]">edit</span>
                    </button>
                  </div>
                </div>

              </div>
              </Reveal>
            ))
          ) : (
            <div className="bg-surface-container-lowest rounded-xl border border-surface-container-high p-14 text-center flex flex-col items-center justify-center">
              <span className="material-symbols-outlined text-[48px] text-surface-container-highest mb-4">event_note</span>
              <h3 className="font-body-lg font-bold text-on-surface mb-2">You haven&apos;t created any events yet</h3>
              <p className="font-body text-on-surface-variant mb-6 max-w-sm">Create your first event and start selling tickets in minutes.</p>
              <Link
                href="/organizer/events/new"
                className="bg-primary text-on-primary rounded-full px-6 py-3 shadow-sm hover:shadow-md transition-shadow font-label-sm flex items-center gap-2"
              >
                <span className="material-symbols-outlined text-[18px]">add</span>
                Create Your First Event
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
