"use client";

import { useEffect } from "react";
import { useReservationStore } from "@/store/reservationStore";
import { useAuthStore } from "@/store/authStore";

export function ReservationGuard() {
  const bookingId = useReservationStore((s) => s.bookingId);
  const { token } = useAuthStore();

  // NOTE: both handlers read `skipBeforeUnload` LIVE via getState() rather than closing over
  // the selector value. A checkout redirect sets the flag and calls window.location.href in the
  // same synchronous tick — before React can re-render and re-bind a fresh handler — so a
  // closed-over value would still be stale and the browser would show "Leave site?".
  useEffect(() => {
    if (!bookingId) return;
    const handler = (e: BeforeUnloadEvent) => {
      if (useReservationStore.getState().skipBeforeUnload) return; // intentional Stripe redirect
      e.preventDefault();
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [bookingId]);

  useEffect(() => {
    if (!bookingId || !token) return;
    const handler = (e: PageTransitionEvent) => {
      // Don't release the hold when the user is intentionally heading to Stripe checkout.
      if (useReservationStore.getState().skipBeforeUnload) return;
      if (!e.persisted) {
        fetch(`${process.env.NEXT_PUBLIC_API_URL}/api/v1/bookings/${bookingId}`, {
          method: "DELETE",
          headers: { Authorization: `Bearer ${token}` },
          keepalive: true,
        }).catch(() => {});
      }
    };
    window.addEventListener("pagehide", handler);
    return () => window.removeEventListener("pagehide", handler);
  }, [bookingId, token]);

  return null;
}
