/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { TrendingUp } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { useReservationStore } from "@/store/reservationStore";
import { api } from "@/lib/api";

// Terminal states have no further action and are de-emphasised in the history table.
const TERMINAL_STATES = new Set(["CANCELLED", "EXPIRED", "PAYMENT_FAILED", "REFUND_APPROVED", "REFUND_DENIED"]);

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
      {state.replace(/_/g, " ")}
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
  expiresAt: string | null;
  quantity: number | null;
  coverImageUrl?: string;
  categoryName?: string;
}

function ReservedCountdown({ expiresAt }: { expiresAt: string }) {
  const [timeLeft, setTimeLeft] = useState(() =>
    Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000))
  );

  // Re-compute every second
  useEffect(() => {
    const id = setInterval(() => {
      setTimeLeft(Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000)));
    }, 1000);
    return () => clearInterval(id);
  }, [expiresAt]);

  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;
  if (timeLeft <= 0) return <span className="text-xs text-error font-medium">Expired</span>;
  return (
    <span className="text-xs text-primary font-medium tabular-nums">
      {minutes}:{seconds.toString().padStart(2, "0")}
    </span>
  );
}

export default function DashboardBookingsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { token, userEmail } = useAuthStore();
  const [resumingId, setResumingId] = useState<number | null>(null);
  const [cancellingId, setCancellingId] = useState<number | null>(null);
  const [resumeError, setResumeError] = useState<string | null>(null);

  // Returning from a cancelled Stripe checkout: drop the in-memory hold so the cart drawer
  // doesn't keep showing a reservation the user walked away from.
  useEffect(() => {
    useReservationStore.getState().clear();
  }, []);

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

  const totalSpent = bookings
    .filter(b => b.state === "CONFIRMED" || b.state === "ATTENDED")
    .reduce((sum, b) => sum + (b.totalAmount || 0), 0);

  const handleResumeCheckout = async (bookingId: number) => {
    setResumingId(bookingId);
    setResumeError(null);
    try {
      const res = await api.post(`/api/bookings/${bookingId}/checkout`);
      const checkoutUrl = res.data?.data?.checkoutUrl;
      if (checkoutUrl) {
        window.location.href = checkoutUrl;
      }
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setResumeError((err as any)?.response?.data?.message || "Could not resume checkout.");
      setResumingId(null);
    }
  };

  const handleCancelBooking = async (bookingId: number) => {
    setCancellingId(bookingId);
    setResumeError(null);
    try {
      await api.delete(`/api/v1/bookings/${bookingId}`);
      await queryClient.invalidateQueries({ queryKey: ["myBookings"] });
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setResumeError((err as any)?.response?.data?.message || "Could not cancel booking.");
    } finally {
      setCancellingId(null);
    }
  };

  if (isLoading) {
    return <main className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen flex items-center justify-center"><p>Loading dashboard...</p></main>;
  }

  return (
    <main className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen flex flex-col gap-section-gap">

      {/* Profile + Stats Section */}
      <section className="grid grid-cols-1 lg:grid-cols-12 gap-gutter">
        {/* Profile Card */}
        <div className="lg:col-span-4 bg-surface-container-lowest rounded-xl shadow-md border border-surface-container-high flex flex-col items-center justify-center text-center relative overflow-hidden">
          <div className="absolute top-0 w-full bg-linear-to-br from-primary-fixed via-primary-container to-secondary-container h-20"></div>
          <div className="w-24 h-24 rounded-full border-4 border-surface-container-lowest shadow-md bg-surface-container-high flex items-center justify-center relative z-10 overflow-hidden mt-10 mb-4">
             <span className="font-hero-headline-mobile text-primary">{userEmail ? userEmail.charAt(0).toUpperCase() : "U"}</span>
          </div>
          <h2 className="font-section-heading text-on-surface mb-1 px-stack-lg">Welcome back, {userName}!</h2>
          <p className="font-body text-on-surface-variant mb-6 px-stack-lg">{userEmail || "user@example.com"}</p>
        </div>

        {/* Stats Grid */}
        <div className="lg:col-span-8 grid grid-cols-1 sm:grid-cols-3 gap-gutter">
          <div className="bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high hover:shadow-xl transition-shadow duration-300 flex flex-col items-start">
            <div className="w-12 h-12 rounded-xl bg-primary-container/20 text-primary flex items-center justify-center mb-4">
              <span className="material-symbols-outlined">confirmation_number</span>
            </div>
            <div className="flex items-end gap-2">
              <p className="text-4xl font-black text-on-surface leading-tight">{totalBookings}</p>
              <TrendingUp className="h-4 w-4 text-emerald-500 mb-1" />
            </div>
            <p className="font-body text-on-surface-variant">Total Bookings</p>
          </div>
          <div className="bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high hover:shadow-xl transition-shadow duration-300 flex flex-col items-start">
            <div className="w-12 h-12 rounded-xl bg-secondary-container/20 text-secondary flex items-center justify-center mb-4">
              <span className="material-symbols-outlined">event_upcoming</span>
            </div>
            <p className="text-4xl font-black text-on-surface leading-tight">{upcomingEvents.length}</p>
            <p className="font-body text-on-surface-variant">Upcoming Events</p>
            <p className="text-xs text-on-surface-variant mt-0.5">Next 30 days: {upcomingNext30}</p>
          </div>
          <div className="bg-surface-container-lowest rounded-xl p-stack-lg shadow-md border border-surface-container-high hover:shadow-xl transition-shadow duration-300 flex flex-col items-start">
            <div className="w-12 h-12 rounded-xl bg-tertiary-container/20 text-tertiary flex items-center justify-center mb-4">
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
                      {booking.tickets?.length || booking.quantity || 0} Tickets
                    </span>
                    <button type="button" className="bg-primary text-on-primary rounded-full px-4 py-2 font-label-sm hover:shadow-md transition-shadow">
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
          <button type="button" className="w-10 h-10 rounded-full border border-surface-container-highest hover:bg-surface-container-low flex items-center justify-center text-on-surface-variant">
            <span className="material-symbols-outlined text-[20px]">filter_list</span>
          </button>
        </div>

        {resumeError && (
          <div className="mb-4 bg-error-container text-on-error-container text-sm rounded-lg px-4 py-3">
            {resumeError}
          </div>
        )}

        {bookings.length > 0 ? (
          <div className="bg-surface-container-lowest rounded-xl shadow-md border border-surface-container-high overflow-x-auto">
            <table className="w-full text-left border-collapse whitespace-nowrap">
              <thead>
                <tr className="bg-surface-container-low text-on-surface-variant border-b border-surface-container-high">
                  <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Event</th>
                  <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Date</th>
                  <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Qty</th>
                  <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Total</th>
                  <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Status</th>
                  <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Action</th>
                </tr>
              </thead>
              <tbody>
                {bookings.map((booking) => {
                  const isReserved = booking.state === "RESERVED";
                  const isPaymentPending = booking.state === "PAYMENT_PENDING";
                  const isRecoverable = isReserved || isPaymentPending;
                  const isTerminal = TERMINAL_STATES.has(booking.state);
                  const holdStillValid = isRecoverable && booking.expiresAt
                    ? new Date(booking.expiresAt).getTime() > Date.now()
                    : false;

                  return (
                    <tr
                      key={booking.id}
                      className={`border-b border-surface-container-high hover:bg-surface-container/50 transition-colors cursor-pointer ${
                        isTerminal ? "opacity-60" : ""
                      }`}
                      onClick={() => router.push(`/dashboard/bookings/${booking.id}`)}
                    >
                      <td className="p-4">
                        <p className="font-body font-bold text-on-surface">{booking.eventTitle}</p>
                        <p className="font-caption text-on-surface-variant">{booking.venueName}</p>
                      </td>
                      <td className="p-4 font-body text-on-surface">
                        {new Date(booking.eventDate).toLocaleDateString()}
                      </td>
                      <td className="p-4 font-body text-on-surface">
                        {booking.quantity || booking.tickets?.length || 0}
                      </td>
                      <td className="p-4 font-body text-on-surface font-semibold">
                        EGP {(booking.totalAmount || 0).toFixed(2)}
                      </td>
                      <td className="p-4">
                        <div className="flex flex-col gap-1">
                          <BookingStatusBadge state={booking.state} />
                          {isRecoverable && booking.expiresAt && (
                            <ReservedCountdown expiresAt={booking.expiresAt} />
                          )}
                        </div>
                      </td>
                      <td className="p-4">
                        {isRecoverable ? (
                          <div className="flex items-center gap-2">
                            {holdStillValid && (
                              <button
                                type="button"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleResumeCheckout(booking.id);
                                }}
                                disabled={resumingId === booking.id || cancellingId === booking.id}
                                className="bg-primary text-on-primary text-xs font-semibold rounded-full px-3 py-1.5 hover:shadow-md transition-shadow disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
                              >
                                {resumingId === booking.id
                                  ? "Redirecting…"
                                  : isPaymentPending
                                    ? "Resume"
                                    : "Continue"}
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleCancelBooking(booking.id);
                              }}
                              disabled={resumingId === booking.id || cancellingId === booking.id}
                              className="text-xs font-semibold rounded-full px-3 py-1.5 border border-outline-variant text-error hover:bg-error-container/40 transition-colors disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
                            >
                              {cancellingId === booking.id ? "Cancelling…" : "Cancel"}
                            </button>
                          </div>
                        ) : (
                          <span className="text-xs text-on-surface-variant">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="bg-surface-container-lowest rounded-xl p-14 border border-surface-container-high text-center flex flex-col items-center">
            <span className="material-symbols-outlined text-[64px] text-surface-container-highest mb-4">receipt_long</span>
            <h3 className="font-body-lg font-bold text-on-surface mb-2">Your history is empty</h3>
            <p className="font-body text-on-surface-variant mb-6 max-w-sm">Once you book an event, your tickets and receipts will show up here.</p>
            <Link href="/search" className="btn-gradient text-on-primary font-label-sm px-6 py-3 rounded-full hover:shadow-lg transition-all">
              Explore Events
            </Link>
          </div>
        )}
      </section>

    </main>
  );
}
