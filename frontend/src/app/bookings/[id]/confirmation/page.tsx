"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { api } from "@/lib/api";
import { useAuthStore } from "@/store/authStore";

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
  totalPrice: number;
  event: {
    title: string;
    startDate: string;
    venueName: string;
    coverImageUrl?: string;
  };
  tickets: Ticket[];
}

export default function ConfirmationPage() {
  const params = useParams<{ id: string }>();
  const bookingId = Number(params.id);
  const { token } = useAuthStore();

  const [isMounted, setIsMounted] = useState(false);
  const [booking, setBooking] = useState<BookingDetails | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [qrsLoaded, setQrsLoaded] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => setIsMounted(true), 0);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    // Wait until the component is mounted on the client.
    // Zustand localStorage persist is synchronous on the client, so by the time this effect runs,
    // the token is guaranteed to be loaded if it exists.
    if (!isMounted) return;

    async function fetchBooking() {
      if (!token) {
        setError("You must be logged in to view your booking.");
        setIsLoading(false);
        return;
      }

      try {
        const res = await api.get(`/api/v1/bookings/${bookingId}`);
        setBooking(res.data?.data);

        // Simulate QR loading for the shimmer effect
        setTimeout(() => setQrsLoaded(true), 2000);
      } catch (err: unknown) {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setError((err as any)?.response?.data?.message || "Failed to load booking details.");
      } finally {
        setIsLoading(false);
      }
    }

    if (bookingId) fetchBooking();
  }, [bookingId, token, isMounted]);

  if (isLoading) {
    return (
      <main className="flex-grow flex items-center justify-center p-8">
        <p className="text-on-surface-variant font-body">Loading booking details...</p>
      </main>
    );
  }

  if (error || !booking) {
    return (
      <main className="flex-grow flex items-center justify-center p-8">
        <div className="bg-error-container text-on-error-container p-6 rounded-xl max-w-md w-full shadow-md text-center">
          <h2 className="font-section-heading mb-2">Error</h2>
          <p className="font-body text-body">{error || "Booking not found."}</p>
        </div>
      </main>
    );
  }

  return (
    <main className="flex-grow flex flex-col items-center justify-center pt-16 pb-section-gap px-edge-padding w-full min-h-screen">
      <div className="max-w-container-max w-full mx-auto max-w-3xl">
        {/* Success Header Area */}
        <div className="text-center mb-stack-lg">
          <svg className="success-checkmark" viewBox="0 0 52 52" xmlns="http://www.w3.org/2000/svg">
            <circle className="checkmark__circle" cx="26" cy="26" fill="none" r="25"></circle>
            <path className="checkmark__check" fill="none" d="M14.1 27.2l7.1 7.2 16.7-16.8"></path>
          </svg>
          <h1 className="font-hero-headline text-hero-headline text-primary mb-stack-sm md:hidden">Booking Confirmed!</h1>
          <h1 className="font-hero-headline text-hero-headline text-primary mb-stack-sm hidden md:block">Booking Confirmed!</h1>
          <p className="font-body-lg text-body-lg text-on-surface-variant mb-stack-md">You&apos;re all set for an amazing experience.</p>

          <div className="inline-flex items-center gap-2 bg-surface-container-high px-4 py-2 rounded-full border border-outline-variant">
            <span className="font-label-sm text-label-sm text-on-surface-variant uppercase">Booking Ref:</span>
            <span className="font-label-sm text-label-sm text-primary font-bold tracking-wider">{booking.reference || `VVD-${booking.id}`}</span>
            <button
              onClick={() => navigator.clipboard.writeText(booking.reference || `VVD-${booking.id}`)}
              aria-label="Copy booking reference"
              className="text-primary hover:text-primary-container transition-colors ml-2"
            >
              <span className="material-symbols-outlined text-[18px]">content_copy</span>
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
                  <span className="material-symbols-outlined text-[14px]">calendar_month</span>
                  {new Date(booking.event.startDate).toLocaleDateString()}
                </span>
              </div>
              <h2 className="font-section-heading text-section-heading text-on-surface mb-1">{booking.event.title}</h2>
              <p className="font-body text-body text-on-surface-variant flex items-center gap-1">
                <span className="material-symbols-outlined text-[18px]">location_on</span> {booking.event.venueName}
              </p>
            </div>
          </div>

          {/* Action/Status Box */}
          <div className="md:col-span-1 bg-gradient-to-br from-primary to-secondary rounded-xl shadow-md p-stack-md text-on-primary flex flex-col justify-between relative overflow-hidden">
            <div className="absolute -top-10 -right-10 w-32 h-32 bg-white/10 rounded-full blur-2xl"></div>
            <div>
              <p className="font-caption text-caption text-primary-fixed mb-1 opacity-90">Total Paid</p>
              <p className="font-hero-headline-mobile text-hero-headline-mobile">${booking.totalPrice?.toFixed(2) || "0.00"}</p>
            </div>
            <div className="mt-4 pt-4 border-t border-white/20">
              <p className="font-caption text-caption flex items-center gap-1">
                <span className="material-symbols-outlined text-[16px]">check_circle</span> Payment Successful
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

                    <div className={`w-32 h-32 bg-white rounded-lg p-2 mb-2 ${!qrsLoaded ? "shimmer" : ""}`}>
                      {qrsLoaded && ticket.qrCode ? (
                        // Medium-17 fix: QR code rendered as <img>
                        <img src={`data:image/png;base64,${ticket.qrCode}`} alt="QR Code" className="w-full h-full object-contain" />
                      ) : qrsLoaded && ticket.code ? (
                        <img src={`https://api.qrserver.com/v1/create-qr-code/?size=128x128&data=${encodeURIComponent(ticket.code)}`} alt="QR Code" className="w-full h-full object-contain" />
                      ) : (
                        <div className="w-full h-full border border-outline-variant/20 rounded"></div>
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
            <span className="material-symbols-outlined transition-transform group-hover:-translate-x-1">home</span>
            Homepage
          </Link>
          <Link href="/dashboard/bookings" className="inline-flex items-center gap-2 bg-surface-container text-on-surface hover:bg-surface-container-high px-6 py-3 rounded-full font-label-lg transition-colors border border-outline-variant">
            <span className="material-symbols-outlined">dashboard</span>
            My Dashboard
          </Link>
        </div>
      </div>
    </main>
  );
}
