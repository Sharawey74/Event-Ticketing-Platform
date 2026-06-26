/* eslint-disable @next/next/no-img-element */
"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { TrendingUp } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

function BookingStatusBadge({ state }: { state: string }) {
  const styleMap: Record<string, string> = {
    RESERVED: "bg-secondary-fixed text-on-secondary-fixed",
    PAYMENT_PENDING: "bg-tertiary-fixed text-on-tertiary-fixed",
    CONFIRMED: "bg-[#e6f4ea] text-[#137333]",
    ATTENDED: "bg-[#e6f4ea] text-[#137333]",
    EXPIRED: "bg-error-container text-on-error-container",
    CANCELLED: "bg-error-container text-on-error-container",
    REFUND_REQUESTED: "bg-primary-fixed text-on-primary-fixed",
    REFUND_APPROVED: "bg-primary-fixed text-on-primary-fixed",
    REFUND_DENIED: "bg-surface-container-high text-on-surface-variant",
    PAYMENT_FAILED: "bg-error-container text-on-error-container",
  };
  const cls = styleMap[state] ?? "bg-surface-container text-on-surface-variant";
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${cls}`}>
      <span className="w-1.5 h-1.5 rounded-full bg-current opacity-70" />
      {state.replace("_", " ")}
    </span>
  );
}

interface Booking {
  id: number;
  state: string;
  eventTitle: string;
  eventDate: string;
  venueName: string;
  tickets: unknown[];
  totalAmount: number;
  coverImageUrl?: string;
  categoryName?: string;
}

export default function DashboardBookingsPage() {
  const router = useRouter();
  const { token, userEmail } = useAuthStore();
  const { data: bookingsData, isLoading } = useQuery({
    queryKey: ["myBookings"],
    queryFn: async () => {
      const res = await api.get("/api/v1/bookings/my");
      return res.data?.data || [];
    },
    enabled: !!token,
  });

  const bookings = (bookingsData as Booking[]) || [];

  const totalBookings = bookings.length;

  const upcomingEvents = bookings.filter(b =>
    b.state === "CONFIRMED" && new Date(b.eventDate).getTime() > new Date().getTime()
  );

  const upcomingNext30 = bookings.filter(b =>
    b.state === "CONFIRMED" &&
    new Date(b.eventDate).getTime() > Date.now() &&
    new Date(b.eventDate).getTime() < Date.now() + 30 * 24 * 60 * 60 * 1000
  ).length;

  const userName = userEmail
    ? userEmail.split("@")[0].replace(/[._-]/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())
    : "there";

  // Expired reservations and other non-confirmed future events are still in history, 
  // but upcoming events specifically look for CONFIRMED future events.

  const totalSpent = bookings
    .filter(b => b.state === "CONFIRMED" || b.state === "ATTENDED")
    .reduce((sum, b) => sum + (b.totalAmount || 0), 0);

  if (isLoading) {
    return <main className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen flex items-center justify-center"><p>Loading dashboard...</p></main>;
  }

  return (
    <main className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen flex flex-col gap-section-gap">
      
      {/* Profile + Stats Section */}
      <section className="grid grid-cols-1 lg:grid-cols-12 gap-gutter">
        {/* Profile Card */}
        <div className="lg:col-span-4 bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high flex flex-col items-center justify-center text-center relative overflow-hidden">
          <div className="absolute top-0 w-full bg-linear-to-r from-primary-container to-secondary opacity-20 h-24"></div>
          <div className="w-24 h-24 rounded-full border-4 border-surface-container-lowest shadow-md bg-surface-container-high flex items-center justify-center relative z-10 overflow-hidden mb-4">
             {/* Initials fallback */}
             <span className="font-hero-headline-mobile text-primary">{userEmail ? userEmail.charAt(0).toUpperCase() : "U"}</span>
          </div>
          <h2 className="font-section-heading text-on-surface mb-1">Welcome back, {userName}!</h2>
          <p className="font-body text-on-surface-variant mb-6">{userEmail || "user@example.com"}</p>
        </div>

        {/* Stats Grid */}
        <div className="lg:col-span-8 grid grid-cols-1 sm:grid-cols-3 gap-gutter">
          <div className="bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high hover:shadow-xl transition-shadow duration-300 flex flex-col items-start">
            <div className="w-12 h-12 rounded-full bg-primary-container/20 text-primary flex items-center justify-center mb-4">
              <span className="material-symbols-outlined">confirmation_number</span>
            </div>
            <div className="flex items-end gap-2">
              <p className="text-4xl font-black text-on-surface leading-tight">{totalBookings}</p>
              <TrendingUp className="h-4 w-4 text-emerald-500 mb-1" />
            </div>
            <p className="font-body text-on-surface-variant">Total Bookings</p>
          </div>
          <div className="bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high hover:shadow-xl transition-shadow duration-300 flex flex-col items-start">
            <div className="w-12 h-12 rounded-full bg-secondary-container/20 text-secondary flex items-center justify-center mb-4">
              <span className="material-symbols-outlined">event_upcoming</span>
            </div>
            <p className="text-4xl font-black text-on-surface leading-tight">{upcomingEvents.length}</p>
            <p className="font-body text-on-surface-variant">Upcoming Events</p>
            <p className="text-xs text-on-surface-variant mt-0.5">Next 30 days: {upcomingNext30}</p>
          </div>
          <div className="bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high hover:shadow-xl transition-shadow duration-300 flex flex-col items-start">
            <div className="w-12 h-12 rounded-full bg-tertiary-container/20 text-tertiary flex items-center justify-center mb-4">
              <span className="material-symbols-outlined">account_balance_wallet</span>
            </div>
            <p className="text-4xl font-black text-on-surface leading-tight">EGP {totalSpent.toFixed(0)}</p>
            <p className="font-body text-on-surface-variant">Total Spent</p>
          </div>
        </div>
      </section>

      {/* Upcoming Events Section */}
      <section>
        <h2 className="font-section-heading text-section-heading text-on-surface mb-stack-md">Upcoming Events</h2>
        
        {upcomingEvents.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-gutter">
            {upcomingEvents.map(booking => (
              <div key={booking.id} className="bg-surface-container-lowest rounded-xl overflow-hidden shadow-md group hover:shadow-xl transition-all duration-300 cursor-pointer flex flex-col" onClick={() => router.push(`/dashboard/bookings/${booking.id}`)}>
                <div className="h-48 relative overflow-hidden bg-surface-container-high">
                  {booking.coverImageUrl ? (
                    <img src={booking.coverImageUrl} alt={booking.eventTitle} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                  ) : (
                    <div className="w-full h-full bg-linear-to-br from-primary to-secondary opacity-50 group-hover:scale-105 transition-transform duration-500" />
                  )}
                  <div className="absolute top-4 right-4 bg-surface-container-lowest/90 backdrop-blur-sm px-3 py-1.5 rounded-full">
                    <span className="font-label-sm text-primary">{booking.categoryName || "Event"}</span>
                  </div>
                </div>
                <div className="p-stack-md flex flex-col justify-between border-x border-b border-surface-container-highest rounded-b-xl grow">
                  <div>
                    <p className="flex items-center gap-2 text-primary font-label-sm mb-2">
                      <span className="material-symbols-outlined text-[16px]">calendar_today</span>
                      {new Date(booking.eventDate).toLocaleDateString()}
                    </p>
                    <h3 className="font-body-lg font-bold text-on-surface line-clamp-1 mb-1">{booking.eventTitle}</h3>
                    <p className="font-caption text-on-surface-variant flex items-center gap-1 mb-4">
                      <span className="material-symbols-outlined text-[14px]">location_on</span>
                      {booking.venueName}
                    </p>
                  </div>
                  <div className="flex items-center justify-between mt-auto">
                    <span className="bg-surface-container px-3 py-1 rounded-full font-label-sm text-on-surface-variant">
                      {booking.tickets?.length || 0} Tickets
                    </span>
                    <button className="bg-primary text-on-primary rounded-full px-4 py-2 font-label-sm hover:shadow-md transition-shadow">
                      View Pass
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="bg-surface-container-lowest rounded-xl p-8 border border-surface-container-high text-center flex flex-col items-center">
            <span className="material-symbols-outlined text-[64px] text-surface-container-highest mb-4">event_busy</span>
            <h3 className="font-body-lg font-bold text-on-surface mb-2">No upcoming events yet</h3>
            <p className="font-body text-on-surface-variant mb-6">Your schedule is clear. Start exploring events and book your next adventure!</p>
            <Link href="/search" className="btn-gradient text-on-primary font-label-sm px-6 py-3 rounded-full hover:shadow-lg transition-all">
              Explore Events
            </Link>
          </div>
        )}
      </section>

      {/* Booking History Section */}
      <section>
        <div className="flex justify-between items-end mb-stack-md">
          <h2 className="font-section-heading text-section-heading text-on-surface">Booking History</h2>
          <button className="w-10 h-10 rounded-full border border-surface-container-highest hover:bg-surface-container-low flex items-center justify-center text-on-surface-variant">
            <span className="material-symbols-outlined text-[20px]">filter_list</span>
          </button>
        </div>

        {bookings.length > 0 ? (
          <>
            <div className="bg-surface-container-lowest rounded-xl shadow-md border border-surface-container-high overflow-x-auto">
              <table className="w-full text-left border-collapse whitespace-nowrap">
                <thead>
                  <tr className="bg-surface-container-low text-on-surface-variant border-b border-surface-container-high">
                    <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Event</th>
                    <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Date</th>
                    <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Qty</th>
                    <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Total</th>
                    <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {bookings.map((booking) => (
                    <tr key={booking.id} className="border-b border-surface-container-high hover:bg-surface-container/50 transition-colors cursor-pointer" onClick={() => router.push(`/dashboard/bookings/${booking.id}`)}>
                      <td className="p-4">
                        <p className="font-body font-bold text-on-surface">{booking.eventTitle}</p>
                        <p className="font-caption text-on-surface-variant">{booking.venueName}</p>
                      </td>
                      <td className="p-4 font-body text-on-surface">
                        {new Date(booking.eventDate).toLocaleDateString()}
                      </td>
                      <td className="p-4 font-body text-on-surface">
                        {booking.tickets?.length || (booking as unknown as Record<string, number>).quantity || 0}
                      </td>
                      <td className="p-4 font-body text-on-surface font-semibold">
                        EGP {(booking.totalAmount || 0).toFixed(2)}
                      </td>
                      <td className="p-4">
                        <BookingStatusBadge state={booking.state} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            
          </>
        ) : (
          <div className="bg-surface-container-lowest rounded-xl p-8 border border-surface-container-high text-center">
            <p className="font-body text-on-surface-variant">Your history is empty.</p>
          </div>
        )}
      </section>

    </main>
  );
}
