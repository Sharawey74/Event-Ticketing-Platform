/* eslint-disable @next/next/no-img-element */
"use client";

import { useState, useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/store/authStore";
import { useReservationStore } from "@/store/reservationStore";
import { api } from "@/lib/api";
import { BookingStatusBadge } from "@/components/bookings/BookingStatusBadge";
import { ArrowLeft, CalendarDays, MapPin, Ticket } from "lucide-react";

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
  expiresAt?: string | null;
  event: {
    title: string;
    startDate: string;
    venueName: string;
    coverImageUrl?: string;
  };
  tickets: Ticket[];
  totalPrice: number;
  refundDenialReason?: string;
}

function TicketCard({ ticket }: { ticket: Ticket }) {
  return (
    <div className="bg-surface-container-lowest rounded-xl shadow-md flex flex-col sm:flex-row border border-outline-variant/30 overflow-hidden relative">
      <div className="absolute top-0 right-0 w-4 h-4 bg-background rounded-bl-full shadow-[inset_1px_-1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>
      <div className="absolute bottom-0 right-0 w-4 h-4 bg-background rounded-tl-full shadow-[inset_1px_1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>
      
      <div className="w-full sm:w-48 bg-surface-container p-4 flex flex-col items-center justify-center border-b sm:border-b-0 sm:border-r border-outline-variant/30 border-dashed relative">
        {/* bg-white is literal on purpose and must not be tokenised: a camera
            reads this at a venue door, and a themed surface would go dark in
            dark mode and stop working. Padding is the quiet zone. */}
        {ticket.qrCode ? (
          <img
            src={`data:image/png;base64,${ticket.qrCode}`}
            alt={`Entry QR code for ticket ${ticket.id}`}
            className="h-44 w-44 rounded-lg bg-white object-contain p-3"
          />
        ) : (
          // No third-party fallback. This previously rendered the code through
          // api.qrserver.com, which put the ticket's entry credential in a
          // query string to an external host.
          <div className="flex h-44 w-44 items-center justify-center rounded-lg border border-dashed border-outline-variant bg-surface-container-high p-3 text-center">
            <span className="font-caption text-[11px] leading-tight text-on-surface-variant">
              QR code not available yet
            </span>
          </div>
        )}
        <p className="font-caption text-caption text-on-surface-variant text-center mt-2">Scan at entry</p>
      </div>
      
      <div className="p-stack-md grow flex flex-col justify-between relative">
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
  
  const queryClient = useQueryClient();
  const [refundStatus, setRefundStatus] = useState<{message: string, isError: boolean} | null>(null);
  const [isRefunding, setIsRefunding] = useState(false);
  const [isCheckingOut, setIsCheckingOut] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      router.push("/auth/login");
    }
  }, [token, router]);

  // Returning from a cancelled Stripe checkout: drop the in-memory hold.
  useEffect(() => {
    useReservationStore.getState().clear();
  }, []);

  const { data: booking, isLoading, error: queryError } = useQuery({
    queryKey: ["booking", id],
    queryFn: async () => {
      const res = await api.get(`/api/v1/bookings/${id}`);
      return res.data?.data as Booking;
    },
    enabled: !!token && !!id,
    retry: false,
  });

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const error = queryError ? (queryError as any)?.response?.data?.message || "Failed to load booking details." : null;

  const handleRefund = async () => {
    if (!confirm("Are you sure you want to request a refund for this booking?")) return;
    
    setIsRefunding(true);
    setRefundStatus(null);
    try {
      const res = await api.post(`/api/v1/bookings/${id}/refunds`);
      const refund = res.data?.data;
      
      queryClient.invalidateQueries({ queryKey: ["booking", id] });
      queryClient.invalidateQueries({ queryKey: ["myBookings"] });
      
      if (refund.status === "DENIED") {
        setRefundStatus({ message: `Refund denied: ${refund.denialReason}`, isError: true });
      } else {
        setRefundStatus({ message: `Refund processed successfully. Amount: EGP ${refund.amount?.toFixed(2) || "0.00"}`, isError: false });
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
      <div className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen flex items-center justify-center">
        <p>Loading booking details...</p>
      </div>
    );
  }

  if (error || !booking) {
    return (
      <div className="pt-32 pb-section-gap px-edge-padding max-w-container-max mx-auto min-h-screen">
        <div className="bg-error-container text-on-error-container p-6 rounded-xl max-w-md mx-auto text-center shadow-md">
          <h2 className="font-section-heading mb-2">Error</h2>
          <p className="font-body text-body">{error || "Booking not found."}</p>
          <Link href="/dashboard/bookings" className="mt-4 inline-block font-label-sm hover:underline">
            Back to Dashboard
          </Link>
        </div>
      </div>
    );
  }

  const handleContinueCheckout = async () => {
    setIsCheckingOut(true);
    setCheckoutError(null);
    try {
      const res = await api.post(`/api/bookings/${id}/checkout`);
      const checkoutUrl = res.data?.data?.checkoutUrl;
      if (checkoutUrl) window.location.href = checkoutUrl;
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setCheckoutError((err as any)?.response?.data?.message || "Could not resume checkout.");
      setIsCheckingOut(false);
    }
  };

  const handleCancelBooking = async () => {
    setIsCancelling(true);
    setCheckoutError(null);
    try {
      await api.delete(`/api/v1/bookings/${id}`);
      await queryClient.invalidateQueries({ queryKey: ["myBookings"] });
      router.push("/dashboard/bookings");
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setCheckoutError((err as any)?.response?.data?.message || "Could not cancel booking.");
      setIsCancelling(false);
    }
  };

  // Calculate days until event
  const eventDate = new Date(booking.event?.startDate || new Date());
  const now = new Date();
  const diffTime = eventDate.getTime() - now.getTime();
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
  const canRefund = booking.state === "CONFIRMED" && diffDays > 3;
  // A denial moves the booking to REFUND_DENIED and records the reason on it.
  // Both gates below previously required CONFIRMED, so once a refund was
  // actually denied the section — and the reason for it — vanished on the next
  // load. The user was told once, transiently, and never again.
  const hasDenialReason = Boolean(booking.refundDenialReason);
  const isRecoverable = booking.state === "RESERVED" || booking.state === "PAYMENT_PENDING";
  const holdStillValid =
    !!booking.expiresAt && new Date(booking.expiresAt).getTime() > Date.now();
  const canContinueCheckout = isRecoverable && holdStillValid;

  return (
    <div className="pt-32 pb-section-gap px-edge-padding max-w-4xl mx-auto min-h-screen flex flex-col gap-stack-lg">
      {/* Back button */}
      <nav>
        <Link href="/dashboard/bookings" className="inline-flex items-center gap-1 text-primary hover:text-primary-container-variant transition-colors font-label-sm">
          <ArrowLeft className="h-[18px] w-[18px]" aria-hidden="true" />
          Back to Dashboard
        </Link>
      </nav>

      {/* Header & Status */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 bg-surface-container-lowest p-stack-lg rounded-xl shadow-md border border-surface-container-high">
        <div>
          <h1 className="font-hero-headline-mobile text-on-surface mb-2">{booking.event?.title}</h1>
          <p className="font-body text-on-surface-variant flex items-center gap-2">
            <CalendarDays className="h-[18px] w-[18px]" aria-hidden="true" />
            {eventDate.toLocaleDateString()} at {eventDate.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}
          </p>
          <p className="font-body text-on-surface-variant flex items-center gap-2 mt-1">
            <MapPin className="h-[18px] w-[18px]" aria-hidden="true" />
            {booking.event?.venueName}
          </p>
        </div>
        <div className="flex flex-col items-end gap-2 text-right">
          <BookingStatusBadge state={booking.state} />
          <p className="font-caption text-on-surface-variant mt-1">Booking Ref: <span className="font-mono text-on-surface">{booking.reference || `VVD-${booking.id}`}</span></p>
          <p className="font-section-heading text-primary mt-2">EGP {(booking.totalPrice || 0).toFixed(2)}</p>
        </div>
      </div>

      {/* Continue/Resume to Checkout — shown for RESERVED or PAYMENT_PENDING bookings with a valid hold */}
      {isRecoverable && (
        <div className="bg-primary/5 border border-primary/20 rounded-xl p-6">
          <div className="flex flex-col md:flex-row justify-between items-center gap-4">
            <div>
              <h3 className="font-label-sm text-on-surface mb-1">Complete Your Purchase</h3>
              <p className="font-caption text-on-surface-variant max-w-lg">
                {holdStillValid
                  ? "Your seat is still held. Continue to secure checkout to confirm this booking."
                  : "This reservation hold has expired. Cancel it to release the seats, then reserve again."}
              </p>
            </div>
            <div className="flex items-center gap-3">
              {canContinueCheckout && (
                <button
                  type="button"
                  onClick={handleContinueCheckout}
                  disabled={isCheckingOut || isCancelling}
                  className="bg-primary text-on-primary px-6 py-2.5 rounded-full font-label-sm hover:shadow-lg transition-shadow disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
                >
                  {isCheckingOut
                    ? "Redirecting…"
                    : booking.state === "PAYMENT_PENDING"
                      ? "Resume Checkout"
                      : "Continue to Checkout"}
                </button>
              )}
              <button
                type="button"
                onClick={handleCancelBooking}
                disabled={isCheckingOut || isCancelling}
                className="px-6 py-2.5 rounded-full font-label-sm border border-outline-variant text-error hover:bg-error-container/40 transition-colors disabled:opacity-60 disabled:cursor-not-allowed whitespace-nowrap"
              >
                {isCancelling ? "Cancelling…" : "Cancel"}
              </button>
            </div>
          </div>
          {checkoutError && (
            <div className="mt-3 bg-error-container text-on-error-container text-sm rounded-lg px-4 py-2">
              {checkoutError}
            </div>
          )}
        </div>
      )}

      {/* Refund Section */}
      {canRefund || refundStatus || hasDenialReason ? (
        <div className="bg-surface-container-low rounded-xl p-6 border border-outline-variant">
          <div className="flex flex-col md:flex-row justify-between items-center gap-4">
            <div>
              <h3 className="font-label-sm text-on-surface mb-1">Manage Booking</h3>
              <p className="font-caption text-on-surface-variant max-w-lg">
                If your plans change, you can request a refund. Full refunds are available up to 7 days before the event, and partial (50%) refunds between 3-6 days.
              </p>
            </div>
            
            {canRefund ? (
              <button
                type="button"
                onClick={handleRefund}
                disabled={isRefunding}
                className="bg-error text-on-error px-6 py-2 rounded-full font-label-sm hover:bg-error-container hover:text-on-error-container transition-colors disabled:opacity-50"
              >
                {isRefunding ? "Processing..." : "Request Refund"}
              </button>
            ) : null}
          </div>
          
          {refundStatus ? (
            <div className={`mt-4 p-4 rounded-lg font-body text-sm ${!refundStatus.isError ? "bg-primary-container text-on-primary-container" : "bg-error-container text-on-error-container"}`}>
              {refundStatus.message}
            </div>
          ) : null}
          
          {hasDenialReason && !refundStatus ? (
            <div className="mt-4 p-4 rounded-lg font-body text-sm bg-error-container text-on-error-container">
              <strong>Refund Denied:</strong> {booking.refundDenialReason}
            </div>
          ) : null}
        </div>
      ) : null}

      {/* Tickets Section */}
      <div>
        <h2 className="font-section-heading text-on-surface mb-stack-md flex items-center gap-2">
          <Ticket className="h-4 w-4" aria-hidden="true" />
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

    </div>
  );
}
