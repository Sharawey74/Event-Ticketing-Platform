"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

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
  const [events, setEvents] = useState<OrganizerEvent[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!token) {
      router.push("/auth/login");
      return;
    }
    
    if (userRole && userRole !== "ORGANIZER") {
      router.push("/dashboard/bookings");
      return;
    }

    async function fetchOrganizerEvents() {
      try {
        const res = await api.get("/api/v1/organizer/events");
        setEvents(res.data?.data || []);
      } catch (err) {
        console.error("Failed to fetch organizer events", err);
      } finally {
        setIsLoading(false);
      }
    }

    fetchOrganizerEvents();
  }, [token, userRole, router]);

  if (isLoading) {
    return <main className="flex-grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen flex items-center justify-center"><p>Loading dashboard...</p></main>;
  }

  return (
    <main className="flex-grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen">
      
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-stack-md mb-stack-lg">
        <div>
          <h1 className="font-hero-headline text-hero-headline text-on-surface">My Events</h1>
          <p className="font-body text-body text-on-surface-variant mt-2">Manage your upcoming venues and track performance.</p>
        </div>
        <Link 
          href="/organizer/events/new" 
          className="bg-primary text-on-primary rounded-full px-6 py-3 shadow-md hover:shadow-xl transition-shadow flex items-center justify-center gap-2 font-label-sm w-fit"
        >
          <span className="material-symbols-outlined text-[20px]">add</span>
          Create New Event
        </Link>
      </div>

      {/* Sales Chart Card */}
      <div className="bg-surface-container-lowest rounded-xl shadow-md p-stack-lg mb-stack-lg border border-outline-variant/30">
        <div className="flex justify-between items-center mb-stack-lg">
          <h2 className="font-section-heading text-section-heading text-on-surface">Sales Over Time</h2>
          <button className="text-on-surface-variant hover:text-primary font-label-sm flex items-center gap-1 transition-colors">
            Last 30 Days
            <span className="material-symbols-outlined text-[18px]">keyboard_arrow_down</span>
          </button>
        </div>
        
        {/* SVG Line Chart Mock */}
        <div className="w-full h-[300px] relative mt-4">
          <svg viewBox="0 0 800 300" className="w-full h-full preserve-aspect-ratio-none">
            <defs>
              <linearGradient id="areaFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#630ed4" stopOpacity="0.2" />
                <stop offset="100%" stopColor="#630ed4" stopOpacity="0" />
              </linearGradient>
            </defs>
            
            {/* Grid Lines */}
            <path d="M 50 50 L 800 50" stroke="#e0e3e5" strokeWidth="1" strokeDasharray="4,4" />
            <path d="M 50 125 L 800 125" stroke="#e0e3e5" strokeWidth="1" strokeDasharray="4,4" />
            <path d="M 50 200 L 800 200" stroke="#e0e3e5" strokeWidth="1" strokeDasharray="4,4" />
            <path d="M 50 275 L 800 275" stroke="#e0e3e5" strokeWidth="1" />
            
            {/* Y-Axis Labels */}
            <text x="40" y="55" fontSize="12" fill="#7b7487" textAnchor="end" className="font-caption">$10k</text>
            <text x="40" y="130" fontSize="12" fill="#7b7487" textAnchor="end" className="font-caption">$5k</text>
            <text x="40" y="205" fontSize="12" fill="#7b7487" textAnchor="end" className="font-caption">$2.5k</text>
            <text x="40" y="280" fontSize="12" fill="#7b7487" textAnchor="end" className="font-caption">$0</text>
            
            {/* X-Axis Labels */}
            <text x="100" y="295" fontSize="12" fill="#7b7487" textAnchor="middle" className="font-caption">Oct 1</text>
            <text x="300" y="295" fontSize="12" fill="#7b7487" textAnchor="middle" className="font-caption">Oct 10</text>
            <text x="500" y="295" fontSize="12" fill="#7b7487" textAnchor="middle" className="font-caption">Oct 20</text>
            <text x="700" y="295" fontSize="12" fill="#7b7487" textAnchor="middle" className="font-caption">Oct 29</text>
            
            {/* Area Fill */}
            <path d="M 100 220 L 200 180 L 300 200 L 400 110 L 500 130 L 600 60 L 700 80 L 700 275 L 100 275 Z" fill="url(#areaFill)" />
            
            {/* Line Path */}
            <path d="M 100 220 L 200 180 L 300 200 L 400 110 L 500 130 L 600 60 L 700 80" fill="none" stroke="#630ed4" strokeWidth="4" />
            
            {/* Data Points */}
            <circle cx="100" cy="220" r="6" fill="#ffffff" stroke="#630ed4" strokeWidth="3" />
            <circle cx="200" cy="180" r="6" fill="#ffffff" stroke="#630ed4" strokeWidth="3" />
            <circle cx="300" cy="200" r="6" fill="#ffffff" stroke="#630ed4" strokeWidth="3" />
            <circle cx="400" cy="110" r="6" fill="#ffffff" stroke="#630ed4" strokeWidth="3" />
            <circle cx="500" cy="130" r="6" fill="#ffffff" stroke="#630ed4" strokeWidth="3" />
            <circle cx="600" cy="60" r="6" fill="#ffffff" stroke="#630ed4" strokeWidth="3" />
            <circle cx="700" cy="80" r="6" fill="#ffffff" stroke="#630ed4" strokeWidth="3" />
          </svg>
        </div>
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
            events.map(event => (
              <div key={event.id} className="bg-surface-container-lowest rounded-xl shadow-md p-stack-md border border-outline-variant/30 flex flex-col lg:flex-row lg:items-center gap-stack-md hover:shadow-xl transition-shadow cursor-pointer" onClick={() => router.push(`/organizer/events/${event.id}/attendees`)}>
                
                {/* Part 1 - Event Info */}
                <div className="flex-1 flex items-center gap-4 min-w-[300px]">
                  <div className="w-32 h-20 rounded-lg overflow-hidden bg-surface-container-high flex-shrink-0">
                    {event.thumbnailUrl ? (
                      <img src={event.thumbnailUrl} alt={event.title} className="w-full h-full object-cover" />
                    ) : (
                      <div className="w-full h-full bg-linear-to-br from-primary-container to-secondary-container" />
                    )}
                  </div>
                  <div>
                    <h3 className="font-body-lg font-bold text-on-surface line-clamp-1">{event.title}</h3>
                    <p className="font-caption text-on-surface-variant mb-2">
                      {new Date(event.date).toLocaleDateString()}
                    </p>
                    <span className={`inline-flex px-2.5 py-1 rounded-full text-[10px] font-bold tracking-wide uppercase ${event.status === 'ACTIVE' ? 'bg-primary-fixed text-on-primary-fixed' : 'bg-surface-container-high text-on-surface-variant'}`}>
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
                  <div className="w-full bg-surface-container-high rounded-full h-2 overflow-hidden">
                    <div 
                      className="bg-primary h-full rounded-full transition-all duration-500 ease-out" 
                      style={{ width: `${Math.min(100, Math.max(0, (event.sold / event.capacity) * 100))}%` }}
                    />
                  </div>
                </div>

                {/* Part 3 - Revenue + Actions */}
                <div className="flex-1 lg:border-l border-outline-variant/30 lg:pl-stack-md flex items-center justify-between py-2 lg:py-0">
                  <div>
                    <p className="font-caption text-on-surface-variant mb-1">Gross Revenue</p>
                    <p className="font-label-sm text-body-lg font-bold text-on-surface">${(event.grossRevenue || 0).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2})}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button 
                      className="w-10 h-10 rounded-full border border-outline-variant hover:text-primary hover:border-primary flex items-center justify-center transition-colors text-on-surface-variant"
                      title="View Attendees"
                      onClick={(e) => { e.stopPropagation(); router.push(`/organizer/events/${event.id}/attendees`); }}
                    >
                      <span className="material-symbols-outlined text-[20px]">group</span>
                    </button>
                    <button 
                      className="w-10 h-10 rounded-full border border-outline-variant hover:text-primary hover:border-primary flex items-center justify-center transition-colors text-on-surface-variant"
                      title="Edit Event"
                      onClick={(e) => { e.stopPropagation(); router.push(`/organizer/events/${event.id}/edit`); }}
                    >
                      <span className="material-symbols-outlined text-[20px]">edit</span>
                    </button>
                  </div>
                </div>

              </div>
            ))
          ) : (
            <div className="bg-surface-container-lowest rounded-xl border border-surface-container-high p-8 text-center flex flex-col items-center justify-center">
              <span className="material-symbols-outlined text-[48px] text-surface-container-highest mb-4">event_note</span>
              <p className="font-body text-on-surface-variant mb-4">You haven&apos;t created any events yet.</p>
              <Link 
                href="/organizer/events/new" 
                className="bg-primary text-on-primary rounded-full px-6 py-2 shadow-sm hover:shadow-md transition-shadow font-label-sm"
              >
                Create Your First Event
              </Link>
            </div>
          )}
        </div>
      </div>
    </main>
  );
}
