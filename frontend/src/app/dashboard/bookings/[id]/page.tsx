"use client";

import { useEffect, useState } from "react";
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

interface Ticket {
  id: number;
  qrCode: string;
  tierName: string;
  gate?: string;
  seatNumber?: string;
  code?: string;
}

interface Booking {
  id: number;
  state: string;
  reference?: string;
  eventTitle: string;
  eventDate: string;
  venueName: string;
  tickets: Ticket[];
  totalAmount: number;
  refundDenialReason?: string;
}

function TicketCard({ ticket }: { ticket: Ticket }) {
  return (
    <div className="bg-surface-container-lowest rounded-xl shadow-md flex flex-col sm:flex-row border border-outline-variant/30 overflow-hidden relative">
      <div className="absolute top-0 right-0 w-4 h-4 bg-background rounded-bl-full shadow-[inset_1px_-1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>
      <div className="absolute bottom-0 right-0 w-4 h-4 bg-background rounded-tl-full shadow-[inset_1px_1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>
      
      <div className="w-full sm:w-48 bg-surface-container p-4 flex flex-col items-center justify-center border-b sm:border-b-0 sm:border-r border-outline-variant/30 border-dashed relative">
        {ticket.qrCode ? (
           <img
             src={`data:image/png;base64,${ticket.qrCode}`}
             alt={`QR Code for ticket ${ticket.id}`}
             className="w-32 h-32 rounded-lg object-contain bg-white p-2"
           />
        ) : (
          <div className="w-32 h-32 border border-outline-variant/20 rounded-lg bg-surface-container-high"></div>
        )}
        <p className="font-caption text-caption text-on-surface-variant text-center mt-2">Scan at entry</p>
      </div>
      
      <div className="p-stack-md flex-grow flex flex-col justify-between relative">
        <div className="absolute top-0 left-0 w-4 h-4 bg-background rounded-br-full shadow-[inset_-1px_-1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>
        <div className="absolute bottom-0 left-0 w-4 h-4 bg-background rounded-tr-full shadow-[inset_-1px_1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>
        
        <div>
          <span className="bg-surface-tint/10 text-primary font-label-sm px-2 py-1 rounded uppercase tracking-wide">
            {ticket.tierName || "General Admission"}
          </span>
          <p className="font-body font-bold text-on-surface mt-2 text-lg">Admit One</p>
          <p className="font-caption font-mono text-on-surface-variant mt-1">ID: {ticket.code || `TCK-${ticket.id}`}</p>
        </div>
        <div className="grid grid-cols-2 gap-4 pt-4 border-t border-outline-variant/30 mt-4">
          <div>
            <p className="font-caption text-on-surface-variant">Gate</p>
            <p className="font-body text-on-surface font-medium">{ticket.gate ?? "Main Gate"}</p>
          </div>
          <div>
            <p className="font-caption text-on-surface-variant">Seat</p>
            <p className="font-body text-on-surface font-medium">{ticket.seatNumber ?? "General"}</p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function BookingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { token } = useAuthStore();
  
  const [booking, setBooking] = useState<Booking | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [refundStatus, setRefundStatus] = useState<{message: string, isError: boolean} | null>(null);
  const [isRefunding, setIsRefunding] = useState(false);

  useEffect(() => {
    if (!token) {
      router.push("/auth");
      return;
    }

    async function fetchBooking() {
      try {
        const res = await api.get(`/api/v1/bookings/${id}`);
        setBooking(res.data?.data);
      } catch (err: unknown) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setError((err as any)?.response?.data?.message || "Failed to load booking details.");
      } finally {
        setIsLoading(false);
      }
    }

    fetchBooking();
  }, [id, token, router]);

  const handleRefund = async () => {
    if (!confirm("Are you sure you want to request a refund for this booking?")) return;
    
    setIsRefunding(true);
    setRefundStatus(null);
    try {
      const res = await api.post(`/api/v1/bookings/${id}/refunds`);
      const refund = res.data?.data;
      
      // Update local state to reflect refund status
      setBooking(prev => prev ? { ...prev, state: refund.status === "DENIED" ? "CONFIRMED" : "REFUND_REQUESTED", refundDenialReason: refund.denialReason } : null);
      
      if (refund.status === "DENIED") {
        setRefundStatus({ message: `Refund denied: ${refund.denialReason}`, isError: true });
      } else {
        setRefundStatus({ message: `Refund processed successfully. Amount: $${refund.amount?.toFixed(2) || "0.00"}`, isError: false });
      }
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setRefundStatus({ message: (err as any)?.response?.data?.message || "Failed to process refund request.", isError: true });
    } finally {
      setIsRefunding(false);
    }
  };

  if (isLoading) {
    return (
      <main className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen flex items-center justify-center">
        <p>Loading booking details...</p>
      </main>
    );
  }

  if (error || !booking) {
    return (
      <main className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen">
        <div className="bg-error-container text-on-error-container p-6 rounded-xl max-w-md mx-auto text-center shadow-md">
          <h2 className="font-section-heading mb-2">Error</h2>
          <p className="font-body text-body">{error || "Booking not found."}</p>
          <Link href="/dashboard/bookings" className="mt-4 inline-block font-label-sm hover:underline">
            Back to Dashboard
          </Link>
        </div>
      </main>
    );
  }

  // Calculate days until event
  const eventDate = new Date(booking.eventDate);
  const now = new Date();
  const diffTime = eventDate.getTime() - now.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  const canRefund = booking.state === "CONFIRMED" && diffDays > 3;

  return (
    <main className="pt-32 pb-section-gap px-edge-padding max-w-4xl mx-auto min-h-screen flex flex-col gap-stack-lg">
      {/* Back button */}
      <nav>
        <Link href="/dashboard/bookings" className="inline-flex items-center gap-1 text-primary hover:text-primary-container-variant transition-colors font-label-sm">
          <span className="material-symbols-outlined text-[18px]">arrow_back</span>
          Back to Dashboard
        </Link>
      </nav>

      {/* Header & Status */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-surface-container-lowest p-stack-lg rounded-xl shadow-md border border-surface-container-high">
        <div>
          <h1 className="font-hero-headline-mobile text-on-surface mb-2">{booking.eventTitle}</h1>
          <p className="font-body text-on-surface-variant flex items-center gap-2">
            <span className="material-symbols-outlined text-[18px]">calendar_month</span>
            {eventDate.toLocaleDateString()} at {eventDate.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
          </p>
          <p className="font-body text-on-surface-variant flex items-center gap-2 mt-1">
            <span className="material-symbols-outlined text-[18px]">location_on</span>
            {booking.venueName}
          </p>
        </div>
        <div className="flex flex-col items-end gap-2 text-right">
          <BookingStatusBadge state={booking.state} />
          <p className="font-caption text-on-surface-variant mt-1">Booking Ref: <span className="font-mono text-on-surface">{booking.reference || `VVD-${booking.id}`}</span></p>
          <p className="font-section-heading text-primary mt-2">${(booking.totalAmount || 0).toFixed(2)}</p>
        </div>
      </div>

      {/* Refund Section */}
      {(canRefund || refundStatus) && (
        <div className="bg-surface-container-lowest p-stack-md rounded-xl shadow-sm border border-surface-container-high">
          <div className="flex flex-col md:flex-row justify-between items-center gap-4">
            <div>
              <h3 className="font-label-sm text-on-surface mb-1">Manage Booking</h3>
              <p className="font-caption text-on-surface-variant max-w-lg">
                If your plans change, you can request a refund. Full refunds are available up to 7 days before the event, and partial (50%) refunds between 3-6 days.
              </p>
            </div>
            
            {canRefund && (
              <button 
                onClick={handleRefund}
                disabled={isRefunding}
                className="w-full md:w-auto border border-error text-error font-label-sm px-6 py-2 rounded-full hover:bg-error-container transition-colors disabled:opacity-50"
              >
                {isRefunding ? "Processing..." : "Request Refund"}
              </button>
            )}
          </div>
          
          {refundStatus && (
            <div className={`mt-4 p-3 rounded-lg text-sm font-medium ${refundStatus.isError ? "bg-error-container text-on-error-container" : "bg-[#e6f4ea] text-[#137333]"}`}>
              {refundStatus.message}
            </div>
          )}
          
          {booking.state === "CONFIRMED" && booking.refundDenialReason && !refundStatus && (
            <div className="mt-4 p-3 rounded-lg text-sm font-medium bg-error-container text-on-error-container">
              Previous refund request denied: {booking.refundDenialReason}
            </div>
          )}
        </div>
      )}

      {/* Tickets Section */}
      <div>
        <h2 className="font-section-heading text-on-surface mb-stack-md flex items-center gap-2">
          <span className="material-symbols-outlined">confirmation_number</span>
          Your Tickets ({booking.tickets?.length || 0})
        </h2>
        
        {booking.tickets && booking.tickets.length > 0 ? (
          <div className="space-y-stack-md">
            {booking.tickets.map(ticket => (
              <TicketCard key={ticket.id} ticket={ticket} />
            ))}
          </div>
        ) : (
          <div className="bg-surface-container-lowest p-8 rounded-xl text-center border border-surface-container-high">
            <p className="font-body text-on-surface-variant">No tickets associated with this booking.</p>
          </div>
        )}
      </div>

    </main>
  );
}
