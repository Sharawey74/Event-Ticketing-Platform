"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { useAuthStore } from "@/store/authStore";
import { AlertCircle, CalendarDays, CheckCircle2, Copy, Home, Hourglass, LayoutDashboard, MapPin, RefreshCw } from "lucide-react";

interface Ticket {
  id: number;
  qrCode: string;
  tierName: string;
  gate?: string;
  seat?: string;
  code: string;
}

interface BookingDetails {
  id: number;
  reference: string;
  state: string;
  totalPrice: number;
  event: {
    title: string;
    startDate: string;
    venueName: string;
    coverImageUrl?: string;
  };
  tickets: Ticket[];
}

/**
 * Ticket QR codes are generated asynchronously. Confirming a booking publishes
 * ticket.generate to RabbitMQ and a consumer writes the codes a few hundred
 * milliseconds later, so the first read straight after reconciliation normally
 * lands before they exist.
 */
const QR_POLL_INTERVAL_MS = 600;
const QR_POLL_TIMEOUT_MS = 12_000;

function awaitingQrCodes(booking: BookingDetails | null | undefined): boolean {
  if (!booking) return false;
  if (booking.state !== "CONFIRMED" && booking.state !== "ATTENDED") return false;
  return (booking.tickets ?? []).some((ticket) => !ticket.qrCode);
}

export default function ConfirmationPage() {
  const params = useParams<{ id: string }>();
  const bookingId = Number(params.id);
  const { token } = useAuthStore();

  const [isMounted, setIsMounted] = useState(false);
  const [booking, setBooking] = useState<BookingDetails | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [qrsLoaded, setQrsLoaded] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const timer = setTimeout(() => setIsMounted(true), 0);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    // Wait until the component is mounted on the client.
    // Zustand localStorage persist is synchronous on the client, so by the time this effect runs,
    // the token is guaranteed to be loaded if it exists.
    if (!isMounted) return;

    let cancelled = false;

    async function fetchBooking() {
      if (!token) {
        setError("You must be logged in to view your booking.");
        setIsLoading(false);
        setIsRefreshing(false);
        return;
      }

      try {
        let res = await api.get(`/api/v1/bookings/${bookingId}`);

        // Confirmation depends on Stripe's asynchronous webhook. If it has not
        // arrived — no endpoint registered, an unreachable host, or a delivery
        // that was simply lost — the booking sits at PAYMENT_PENDING while the
        // card has already been charged, and the expiry job eventually marks it
        // PAYMENT_FAILED.
        //
        // Stripe returns session_id on this redirect precisely so we can verify
        // directly. Ask the server to reconcile, then re-read. Idempotent, and a
        // no-op for any booking that is already settled.
        if (res.data?.data?.state === "PAYMENT_PENDING") {
          try {
            await api.post(`/api/v1/bookings/${bookingId}/sync-payment`);
            res = await api.get(`/api/v1/bookings/${bookingId}`);
          } catch {
            // Reconciliation is best-effort: the webhook may still land, and the
            // Refresh Status button retries. Never block rendering on it.
          }
        }

        let data: BookingDetails | null = res.data?.data ?? null;
        setBooking(data);
        setIsLoading(false);

        // Wait for the codes rather than pretending to. This used to be a flat
        // two-second timer that revealed whatever the single fetch had already
        // returned — so on a fresh confirmation the tickets rendered with no QR
        // at all, and only looked right later because a third-party fallback
        // drew one from the ticket code. That fallback is gone, so the page has
        // to actually wait for the real image.
        const deadline = Date.now() + QR_POLL_TIMEOUT_MS;
        while (!cancelled && awaitingQrCodes(data) && Date.now() < deadline) {
          await new Promise((resolve) => setTimeout(resolve, QR_POLL_INTERVAL_MS));
          if (cancelled) return;
          const retry = await api.get(`/api/v1/bookings/${bookingId}`);
          data = retry.data?.data ?? null;
          setBooking(data);
        }

        if (!cancelled) setQrsLoaded(true);
      } catch (err: unknown) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setError((err as any)?.response?.data?.message || "Failed to load booking details.");
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    }

    if (bookingId) fetchBooking();
    return () => {
      cancelled = true;
    };
  }, [bookingId, token, isMounted, refreshKey]);

  const handleRefreshStatus = () => {
    setIsRefreshing(true);
    setRefreshKey((k) => k + 1);
  };

  if (isLoading) {
    return (
      <div className="flex-grow flex items-center justify-center p-8">
        <p className="text-on-surface-variant font-body">Loading booking details...</p>
      </div>
    );
  }

  if (error || !booking) {
    return (
      <div className="flex-grow flex items-center justify-center p-8">
        <div className="bg-error-container text-on-error-container p-6 rounded-xl max-w-md w-full shadow-md text-center">
          <h2 className="font-section-heading mb-2">Error</h2>
          <p className="font-body text-body">{error || "Booking not found."}</p>
        </div>
      </div>
    );
  }

  const isConfirmed = booking.state === "CONFIRMED" || booking.state === "ATTENDED";
  const isFailed = booking.state === "PAYMENT_FAILED" || booking.state === "CANCELLED" || booking.state === "EXPIRED";

  return (
    <div className="flex-grow flex flex-col items-center justify-center pt-16 pb-section-gap px-edge-padding w-full min-h-screen">
      <div className="max-w-container-max w-full mx-auto max-w-3xl">
        {/* Status Header Area */}
        <div className="text-center mb-stack-lg">
          {isConfirmed ? (
            <>
              <svg className="success-checkmark" viewBox="0 0 52 52" xmlns="http://www.w3.org/2000/svg">
                <circle className="checkmark__circle" cx="26" cy="26" fill="none" r="25"></circle>
                <path className="checkmark__check" fill="none" d="M14.1 27.2l7.1 7.2 16.7-16.8"></path>
              </svg>
              <h1 className="font-hero-headline text-hero-headline text-primary mb-stack-sm">Booking Confirmed!</h1>
              <p className="font-body-lg text-body-lg text-on-surface-variant mb-stack-md">You&apos;re all set for an amazing experience.</p>
            </>
          ) : isFailed ? (
            <>
              <h1 className="font-hero-headline text-hero-headline text-error mb-stack-sm">Payment Not Completed</h1>
              <p className="font-body-lg text-body-lg text-on-surface-variant mb-stack-md">
                This booking is currently <span className="font-semibold">{booking.state}</span>. If you were charged, contact support with your booking reference below.
              </p>
            </>
          ) : (
            <>
              <h1 className="font-hero-headline text-hero-headline text-primary mb-stack-sm">Payment Processing</h1>
              <p className="font-body-lg text-body-lg text-on-surface-variant mb-stack-md">
                We&apos;re confirming your payment with Stripe — this can take a few moments.
              </p>
              <button
                onClick={handleRefreshStatus}
                disabled={isRefreshing}
                className="inline-flex items-center gap-2 bg-surface-container text-on-surface hover:bg-surface-container-high px-5 py-2 rounded-full font-label-lg transition-colors border border-outline-variant disabled:opacity-60 mb-stack-md"
              >
                <RefreshCw className="h-[18px] w-[18px]" aria-hidden="true" />
                {isRefreshing ? "Checking..." : "Refresh Status"}
              </button>
            </>
          )}

          <div className="inline-flex items-center gap-2 bg-surface-container-high px-4 py-2 rounded-full border border-outline-variant">
            <span className="font-label-sm text-label-sm text-on-surface-variant uppercase">Booking Ref:</span>
            <span className="font-label-sm text-label-sm text-primary font-bold tracking-wider">{booking.reference || `VVD-${booking.id}`}</span>
            <button
              onClick={() => navigator.clipboard.writeText(booking.reference || `VVD-${booking.id}`)}
              aria-label="Copy booking reference"
              className="text-primary hover:text-primary-container transition-colors ml-2"
            >
              <Copy className="h-[18px] w-[18px]" aria-hidden="true" />
            </button>
          </div>
        </div>

        {/* Event Summary Bento Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-gutter mb-section-gap">
          {/* Main Event Details */}
          <div className="md:col-span-2 bg-surface-container-lowest rounded-xl shadow-md p-stack-md border border-outline-variant/30 flex flex-col sm:flex-row gap-stack-md items-start sm:items-center relative overflow-hidden group hover:shadow-xl transition-shadow duration-300">
            <div className="w-full sm:w-32 h-32 rounded-lg bg-surface-container-high flex-shrink-0 relative overflow-hidden">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={booking.event.coverImageUrl || ""} alt="Event Cover" className="w-full h-full object-cover" />
            </div>
            <div className="flex-grow">
              <div className="flex items-center gap-2 mb-2">
                <span className="bg-primary-fixed text-on-primary-fixed font-label-sm text-label-sm px-3 py-1 rounded-full">Event</span>
                <span className="text-on-surface-variant text-caption flex items-center gap-1">
                  <CalendarDays className="h-[14px] w-[14px]" aria-hidden="true" />
                  {new Date(booking.event.startDate).toLocaleDateString()}
                </span>
              </div>
              <h2 className="font-section-heading text-section-heading text-on-surface mb-1">{booking.event.title}</h2>
              <p className="font-body text-body text-on-surface-variant flex items-center gap-1">
                <MapPin className="h-[18px] w-[18px]" aria-hidden="true" /> {booking.event.venueName}
              </p>
            </div>
          </div>

          {/* Action/Status Box */}
          <div className="md:col-span-1 bg-gradient-to-br from-primary to-secondary rounded-xl shadow-md p-stack-md text-on-primary flex flex-col justify-between relative overflow-hidden">
            <div className="absolute -top-10 -right-10 w-32 h-32 bg-white/10 rounded-full blur-2xl"></div>
            <div>
              <p className="font-caption text-caption text-primary-fixed mb-1 opacity-90">Total Paid</p>
              <p className="font-hero-headline-mobile text-hero-headline-mobile">EGP {booking.totalPrice?.toFixed(2) || "0.00"}</p>
            </div>
            <div className="mt-4 pt-4 border-t border-white/20">
              <p className="font-caption text-caption flex items-center gap-1">
                {/* The icon repeats the state the sentence beside it already
                    gives, so it is decorative and stays out of the name. */}
                {isConfirmed ? (
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                ) : isFailed ? (
                  <AlertCircle className="h-4 w-4" aria-hidden="true" />
                ) : (
                  <Hourglass className="h-4 w-4" aria-hidden="true" />
                )}
                {isConfirmed ? "Payment Successful" : isFailed ? "Payment Not Completed" : "Payment Processing"}
              </p>
            </div>
          </div>
        </div>

        {/* Tickets Section */}
        {booking.tickets && booking.tickets.length > 0 && (
          <div className="mb-section-gap">
            <h3 className="font-section-heading text-section-heading text-on-surface mb-stack-md">Your Tickets</h3>
            <div className="space-y-stack-md">
              {booking.tickets.map((ticket, index) => (
                <div key={ticket.id || index} className="bg-surface-container-lowest rounded-xl shadow-md flex flex-col sm:flex-row border border-outline-variant/30 overflow-hidden hover:shadow-xl transition-shadow duration-300">
                  {/* Left: QR Area */}
                  <div className="w-full sm:w-48 bg-surface-container p-4 flex flex-col items-center justify-center border-b sm:border-b-0 sm:border-r border-outline-variant/30 border-dashed relative">
                    <div className="absolute top-0 right-0 w-4 h-4 bg-background rounded-bl-full shadow-[inset_1px_-1px_0_rgba(204,195,216,0.3)]"></div>
                    <div className="absolute bottom-0 right-0 w-4 h-4 bg-background rounded-tl-full shadow-[inset_1px_1px_0_rgba(204,195,216,0.3)]"></div>

                    {/* bg-white is literal on purpose and must not be tokenised:
                        this is scanned by a camera at a venue door. A themed
                        surface would go dark in dark mode and stop working.
                        Sized up from 128px, with padding as the quiet zone. */}
                    <div className={`mb-2 h-44 w-44 rounded-lg bg-white p-3 ${!qrsLoaded ? "shimmer" : ""}`}>
                      {qrsLoaded && ticket.qrCode ? (
                        <img
                          src={`data:image/png;base64,${ticket.qrCode}`}
                          alt={`Entry QR code for ticket ${ticket.code ?? index + 1}`}
                          className="h-full w-full object-contain"
                        />
                      ) : qrsLoaded ? (
                        // No third-party fallback. This previously rendered the
                        // code through api.qrserver.com, which put the ticket's
                        // entry credential in a query string to an external
                        // host. The backend already generates the QR; if it is
                        // missing, say so rather than leaking it.
                        <div className="flex h-full w-full items-center justify-center rounded border border-dashed border-outline-variant p-2 text-center">
                          <span className="font-caption text-[11px] leading-tight text-on-surface-variant">
                            Still generating — refresh in a moment
                          </span>
                        </div>
                      ) : (
                        <div className="h-full w-full rounded border border-outline-variant/20" />
                      )}
                    </div>
                    <p className="font-caption text-caption text-on-surface-variant text-center">Scan at entry</p>
                  </div>

                  {/* Right: Ticket Details */}
                  <div className="p-stack-md flex-grow flex flex-col justify-between relative">
                    <div className="absolute top-0 left-0 w-4 h-4 bg-background rounded-br-full shadow-[inset_-1px_-1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>
                    <div className="absolute bottom-0 left-0 w-4 h-4 bg-background rounded-tr-full shadow-[inset_-1px_1px_0_rgba(204,195,216,0.3)] hidden sm:block"></div>

                    <div className="flex justify-between items-start mb-4">
                      <div>
                        <span className="bg-surface-tint/10 text-primary font-label-sm text-label-sm px-2 py-1 rounded uppercase tracking-wide">
                          {ticket.tierName || "General Admission"}
                        </span>
                        <p className="font-body text-body text-on-surface font-semibold mt-2">Admit One</p>
                      </div>
                      <div className="text-right">
                        <p className="font-caption text-caption text-on-surface-variant">Ticket ID</p>
                        <p className="font-body text-body font-mono text-on-surface">{ticket.code || `TCK-${ticket.id}`}</p>
                      </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4 pt-4 border-t border-outline-variant/30">
                      <div>
                        <p className="font-caption text-caption text-on-surface-variant">Gate</p>
                        <p className="font-body text-body text-on-surface">{ticket.gate || "Main Gate"}</p>
                      </div>
                      <div>
                        <p className="font-caption text-caption text-on-surface-variant">Seat</p>
                        <p className="font-body text-body text-on-surface">{ticket.seat || "General"}</p>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row gap-4 justify-center items-center">
          <Link href="/" className="w-full sm:w-auto bg-gradient-to-r from-primary to-secondary text-on-primary font-body text-body font-semibold py-3 px-8 rounded-full shadow-md hover:shadow-xl transition-all duration-300 flex items-center justify-center gap-2 group">
            <Home className="h-4 w-4 transition-transform group-hover:-translate-x-1" aria-hidden="true" />
            Homepage
          </Link>
          <Link href="/dashboard/bookings" className="inline-flex items-center gap-2 bg-surface-container text-on-surface hover:bg-surface-container-high px-6 py-3 rounded-full font-label-lg transition-colors border border-outline-variant">
            <LayoutDashboard className="h-4 w-4" aria-hidden="true" />
            My Dashboard
          </Link>
        </div>
      </div>
    </div>
  );
}
