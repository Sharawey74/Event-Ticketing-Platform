"use client";

import { useEffect } from "react";
import { useReservationStore } from "@/store/reservationStore";
import { useAuthStore } from "@/store/authStore";

export function ReservationGuard() {
  const bookingId = useReservationStore((s) => s.bookingId);
  const { token } = useAuthStore();

  useEffect(() => {
    if (!bookingId) return;
    const handler = (e: BeforeUnloadEvent) => { e.preventDefault(); };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [bookingId]);

  useEffect(() => {
    if (!bookingId || !token) return;
    const handler = (e: PageTransitionEvent) => {
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
