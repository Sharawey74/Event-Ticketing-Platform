"use client";

import { useEffect, useState } from "react";
import { X, ShoppingCart, Timer, ArrowRight } from "lucide-react";
import { useRouter } from "next/navigation";

import { useReservationStore } from "@/store/reservationStore";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

const SERVICE_FEE = 25;

interface CartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

export function CartDrawer({ isOpen, onClose }: CartDrawerProps) {
  const router = useRouter();
  const store = useReservationStore();
  const { token } = useAuthStore();
  const [timeLeft, setTimeLeft] = useState(0);
  const [isCancelling, setIsCancelling] = useState(false);

  useEffect(() => {
    if (!store.expiresAt) {
      setTimeLeft(0);
      return;
    }
    const calc = () =>
      Math.max(0, Math.floor((new Date(store.expiresAt!).getTime() - Date.now()) / 1000));
    setTimeLeft(calc());
    const interval = setInterval(() => setTimeLeft(calc()), 1000);
    return () => clearInterval(interval);
  }, [store.expiresAt]);

  const handleCheckout = () => {
    if (!store.returnPath) return;
    onClose();
    router.push(store.returnPath);
  };

  const handleCancel = async () => {
    if (!store.bookingId || !token) return;
    setIsCancelling(true);
    try {
      await api.delete(`/api/v1/bookings/${store.bookingId}`);
    } catch {
      // booking may already be expired — clear store anyway
    } finally {
      store.clear();
      setIsCancelling(false);
      onClose();
    }
  };

  const subtotal = store.unitPrice * store.quantity;
  const total = subtotal + SERVICE_FEE;
  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;

  return (
    <>
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/30 backdrop-blur-sm"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <div
        className={`fixed top-0 right-0 z-50 h-full w-full max-w-sm bg-surface shadow-2xl flex flex-col transition-transform duration-300 ease-in-out ${
          isOpen ? "translate-x-0" : "translate-x-full"
        }`}
        role="dialog"
        aria-modal="true"
        aria-label="Cart"
      >
        <div className="flex items-center justify-between border-b border-outline-variant px-6 py-4">
          <div className="flex items-center gap-2">
            <ShoppingCart className="h-5 w-5 text-primary" />
            <h2 className="font-section-heading text-on-surface">Your Reservation</h2>
          </div>
          <button
            onClick={onClose}
            className="text-on-surface-variant hover:text-on-surface transition-colors"
            aria-label="Close cart"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-6">
          {store.bookingId ? (
            <div className="flex flex-col gap-5">
              <div>
                <p className="text-xs uppercase tracking-wider text-on-surface-variant mb-1">Event</p>
                <p className="font-semibold text-on-surface">{store.eventTitle}</p>
                <p className="text-sm text-on-surface-variant mt-1">
                  {store.tierName} × {store.quantity}
                </p>
              </div>

              {timeLeft > 0 ? (
                <div className="bg-primary/10 border border-primary/20 rounded-xl p-4 flex items-center gap-3">
                  <Timer className="h-5 w-5 text-primary shrink-0" />
                  <div>
                    <p className="text-sm font-semibold text-primary">Hold expires in</p>
                    <p className="text-2xl font-bold text-primary tabular-nums">
                      {minutes}:{seconds.toString().padStart(2, "0")}
                    </p>
                  </div>
                </div>
              ) : (
                <div className="bg-error/10 border border-error/20 rounded-xl p-4">
                  <p className="text-sm font-semibold text-error">Reservation expired</p>
                  <p className="text-xs text-on-surface-variant mt-1">
                    Please select tickets again.
                  </p>
                </div>
              )}

              <div className="bg-surface-container-low rounded-xl p-4 space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-on-surface-variant">
                    Ticket × {store.quantity}
                  </span>
                  <span className="text-on-surface">EGP {subtotal.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-on-surface-variant">Service fee</span>
                  <span className="text-on-surface">EGP {SERVICE_FEE.toFixed(2)}</span>
                </div>
                <div className="h-px bg-outline-variant" />
                <div className="flex justify-between font-semibold">
                  <span className="text-on-surface">Total</span>
                  <span className="text-primary text-lg">EGP {total.toFixed(2)}</span>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center h-full gap-4 text-center py-16">
              <ShoppingCart className="h-12 w-12 text-outline" />
              <p className="font-medium text-on-surface">Your cart is empty</p>
              <p className="text-sm text-on-surface-variant">
                Browse events and reserve your spot.
              </p>
              <button
                onClick={() => { onClose(); router.push("/"); }}
                className="text-sm text-primary hover:underline"
              >
                Discover Events
              </button>
            </div>
          )}
        </div>

        {store.bookingId && (
          <div className="border-t border-outline-variant px-6 py-4 flex flex-col gap-3">
            <button
              type="button"
              onClick={handleCheckout}
              disabled={timeLeft <= 0}
              className="w-full btn-gradient text-on-primary font-semibold h-12 rounded-full flex items-center justify-center gap-2 hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Proceed to Checkout
              <ArrowRight className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={handleCancel}
              disabled={isCancelling}
              className="w-full text-sm text-error hover:underline transition-colors py-1"
            >
              {isCancelling ? "Cancelling…" : "Cancel Reservation"}
            </button>
          </div>
        )}
      </div>
    </>
  );
}
