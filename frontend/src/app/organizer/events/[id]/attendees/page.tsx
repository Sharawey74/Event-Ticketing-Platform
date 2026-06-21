"use client";

import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
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

interface Attendee {
  bookingId: number;
  attendeeName: string;
  email: string;
  tierName: string;
  state: string;
  reference?: string;
}

export default function EventAttendeesPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { token, userRole } = useAuthStore();
  
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState("");
  const [isCheckingIn, setIsCheckingIn] = useState<number | null>(null);

  const { data: attendeesData, isLoading } = useQuery({
    queryKey: ["eventAttendees", id],
    queryFn: async () => {
      const res = await api.get(`/api/v1/organizer/events/${id}/attendees`);
      return res.data?.data || [];
    },
    enabled: !!token && userRole === "ORGANIZER" && !!id,
  });

  const attendees = (attendeesData as Attendee[]) || [];

  useEffect(() => {
    if (!token) {
      router.push("/auth/login");
      return;
    }

    if (userRole && userRole !== "ORGANIZER") {
      router.push("/dashboard/bookings");
      return;
    }
  }, [token, userRole, router]);

  const handleCheckIn = async (bookingId: number) => {
    setIsCheckingIn(bookingId);
    try {
      await api.post(`/api/v1/bookings/${bookingId}/check-ins`);
      queryClient.invalidateQueries({ queryKey: ["eventAttendees", id] });
    } catch (err) {
      console.error("Check-in failed", err);
      alert("Failed to check-in attendee. They might not be in a CONFIRMED state or you are not authorized.");
    } finally {
      setIsCheckingIn(null);
    }
  };

  const filteredAttendees = attendees.filter(a => 
    a.attendeeName.toLowerCase().includes(searchQuery.toLowerCase()) || 
    a.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (a.reference && a.reference.toLowerCase().includes(searchQuery.toLowerCase()))
  );

  if (isLoading) {
    return <main className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen flex items-center justify-center"><p>Loading attendees...</p></main>;
  }

  return (
    <main className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen">
      
      {/* Back button */}
      <nav className="mb-stack-md">
        <Link href="/organizer/events" className="inline-flex items-center gap-1 text-primary hover:text-primary-container-variant transition-colors font-label-sm">
          <span className="material-symbols-outlined text-[18px]">arrow_back</span>
          Back to Events
        </Link>
      </nav>

      {/* Header & Search */}
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-stack-md mb-stack-lg">
        <div>
          <h1 className="font-hero-headline-mobile text-on-surface">Event Attendees</h1>
          <p className="font-body text-body text-on-surface-variant mt-1">Manage guest check-ins for this event.</p>
        </div>
        <div className="relative w-full md:w-80">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]">search</span>
          <input 
            type="text" 
            placeholder="Search by name, email, or ref..." 
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-full border border-outline-variant py-2 pl-10 pr-4 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body text-sm"
          />
        </div>
      </div>

      {/* Attendee Table */}
      <div className="bg-surface-container-lowest rounded-xl shadow-md border border-surface-container-high overflow-x-auto">
        <table className="w-full text-left border-collapse whitespace-nowrap">
          <thead>
            <tr className="bg-surface-container-low text-on-surface-variant border-b border-surface-container-high">
              <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Attendee Name</th>
              <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Email</th>
              <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Tier</th>
              <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Booking Ref</th>
              <th className="p-4 font-label-sm uppercase tracking-wider font-semibold">Status</th>
              <th className="p-4 font-label-sm uppercase tracking-wider font-semibold text-right">Action</th>
            </tr>
          </thead>
          <tbody>
            {filteredAttendees.length > 0 ? (
              filteredAttendees.map(attendee => (
                <tr key={attendee.bookingId} className="border-b border-surface-container-high hover:bg-surface-container/30 transition-colors">
                  <td className="p-4 font-body font-bold text-on-surface">
                    {attendee.attendeeName}
                  </td>
                  <td className="p-4 font-body text-on-surface-variant">
                    {attendee.email}
                  </td>
                  <td className="p-4">
                    <span className="bg-surface-container px-2 py-1 rounded text-xs uppercase font-semibold text-on-surface-variant">
                      {attendee.tierName}
                    </span>
                  </td>
                  <td className="p-4 font-mono text-sm text-on-surface">
                    {attendee.reference || `BKG-${attendee.bookingId}`}
                  </td>
                  <td className="p-4">
                    <BookingStatusBadge state={attendee.state} />
                  </td>
                  <td className="p-4 text-right">
                    {attendee.state === "CONFIRMED" ? (
                      <button 
                        onClick={() => handleCheckIn(attendee.bookingId)}
                        disabled={isCheckingIn === attendee.bookingId}
                        className="bg-primary text-on-primary font-label-sm px-4 py-1.5 rounded-full hover:bg-primary-container-variant transition-colors disabled:opacity-50"
                      >
                        {isCheckingIn === attendee.bookingId ? "Processing..." : "Check In"}
                      </button>
                    ) : (
                      <button disabled className="bg-surface-container-highest text-on-surface-variant font-label-sm px-4 py-1.5 rounded-full opacity-50 cursor-not-allowed">
                        {attendee.state === "ATTENDED" ? "Checked In" : "Unavailable"}
                      </button>
                    )}
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={6} className="p-8 text-center text-on-surface-variant font-body">
                  No attendees found matching your search criteria.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

    </main>
  );
}
