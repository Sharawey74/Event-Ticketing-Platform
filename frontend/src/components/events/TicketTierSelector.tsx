"use client";

import { useState, useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { TicketTierResponse } from "@/types/event";
import { api } from "@/lib/api";
import { useAuthStore } from "@/store/authStore";
import { useReservationStore } from "@/store/reservationStore";

interface TicketTierSelectorProps {
  eventId: number;
  eventTitle: string;
  tiers: TicketTierResponse[];
}

export function TicketTierSelector({ eventId, eventTitle, tiers }: TicketTierSelectorProps) {
  const router = useRouter();
  const pathname = usePathname();
  const { token } = useAuthStore();
  const { set: reservationSet, clear: reservationClear } = useReservationStore();
  
  const [selectedTierId, setSelectedTierId] = useState<number | null>(
    tiers.length > 0 ? tiers[0].id : null
  );
  const [quantity, setQuantity] = useState<number>(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reservation state
  const [bookingId, setBookingId] = useState<number | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [timeLeft, setTimeLeft] = useState<number>(0);

  const selectedTier = tiers.find((t) => t.id === selectedTierId);
  const price = selectedTier ? selectedTier.basePrice : 0;
  const fee = 25; // Example fee
  const total = (price + fee) * quantity;

  useEffect(() => {
    if (!expiresAt) return;

    const calculateTimeLeft = () => {
      const now = new Date().getTime();
      const expiry = new Date(expiresAt).getTime();
      return Math.max(0, Math.floor((expiry - now) / 1000));
    };

    const interval = setInterval(() => setTimeLeft(calculateTimeLeft()), 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  // beforeunload dialog and cancellation are handled globally by ReservationGuard

  const handleDecrease = () => {
    if (quantity > 1) setQuantity(quantity - 1);
  };

  const handleIncrease = () => {
    if (quantity < 10) setQuantity(quantity + 1);
  };

  const handleReserve = async () => {
    if (!token) {
      router.push("/auth");
      return;
    }

    if (!selectedTierId) return;

    setIsSubmitting(true);
    setError(null);

    try {
      const idempotencyKey = crypto.randomUUID();
      const res = await api.post(
        "/api/v1/bookings",
        {
          eventId,
          tierId: selectedTierId,
          quantity,
        },
        {
          headers: {
            "Idempotency-Key": idempotencyKey,
          },
        }
      );

      const booking = res.data?.data;
      if (booking && booking.bookingId) {
        setBookingId(booking.bookingId);
        setExpiresAt(booking.expiresAt);
        const now = new Date().getTime();
        const expiry = new Date(booking.expiresAt).getTime();
        setTimeLeft(Math.max(0, Math.floor((expiry - now) / 1000)));
        reservationSet(
          booking.bookingId,
          booking.expiresAt,
          pathname ?? "/",
          eventTitle,
          selectedTier?.tierName ?? "",
          quantity,
          selectedTier?.basePrice ?? 0,
        );
      } else {
        throw new Error("Invalid response from server");
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setError((err as any)?.response?.data?.message || "Failed to reserve tickets.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCheckout = async () => {
    if (!bookingId) return;
    setIsSubmitting(true);
    setError(null);

    try {
      // Create Stripe checkout session
      const res = await api.post(`/api/bookings/${bookingId}/checkout`);
      const checkoutUrl = res.data?.data?.checkoutUrl;
      
      if (checkoutUrl) {
        reservationClear();
        window.location.href = checkoutUrl;
      } else {
        throw new Error("Checkout URL missing");
      }
    } catch (err: unknown) {
       // eslint-disable-next-line @typescript-eslint/no-explicit-any
       setError((err as any)?.response?.data?.message || "Failed to initiate checkout.");
       setIsSubmitting(false);
    }
  };

  if (!tiers || tiers.length === 0) {
    return (
      <div className="bg-surface-container-lowest rounded-xl shadow-xl p-6 border border-outline-variant/30">
        <h2 className="font-section-heading text-section-heading text-on-surface mb-4">Tickets</h2>
        <p className="text-on-surface-variant font-body text-body">Tickets are currently unavailable.</p>
      </div>
    );
  }

  if (bookingId && expiresAt) {
    const minutes = Math.floor(timeLeft / 60);
    const seconds = timeLeft % 60;
    
    return (
      <div className="sticky top-28 bg-surface-container-lowest rounded-xl shadow-xl p-6 border border-outline-variant/30 flex flex-col gap-stack-md">
        <h2 className="font-section-heading text-section-heading text-on-surface">Order Summary</h2>
        
        {error && (
          <div className="bg-error-container text-on-error-container p-3 rounded-lg text-sm font-medium">
            {error}
          </div>
        )}

        <div className="bg-surface-container-low rounded-xl p-4 border border-outline-variant/30 mb-4 space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-on-surface-variant">{selectedTier?.tierName} × {quantity}</span>
            <span className="text-on-surface">EGP {(price * quantity).toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-on-surface-variant">Service fee</span>
            <span className="text-on-surface">EGP {fee.toFixed(2)}</span>
          </div>
          <div className="h-px bg-outline-variant" />
          <div className="flex justify-between font-semibold">
            <span className="text-on-surface">Total</span>
            <span className="text-primary text-lg">EGP {total.toFixed(2)}</span>
          </div>
        </div>

        {timeLeft > 0 ? (
          <div className="bg-primary/10 border border-primary/20 rounded-xl p-4 mb-4 flex items-center gap-3">
            <span className="material-symbols-outlined text-primary">timer</span>
            <div>
              <p className="font-label-sm text-label-sm text-primary">Reservation Held</p>
              <p className="font-caption text-caption text-on-surface-variant">
                Completing order in {minutes}:{seconds.toString().padStart(2, "0")}
              </p>
            </div>
          </div>
        ) : (
          <div className="bg-error/10 border border-error/20 rounded-xl p-4 mb-4">
            <p className="font-label-sm text-label-sm text-error">Reservation Expired</p>
            <p className="font-caption text-caption text-on-surface-variant">Please select your tickets again.</p>
          </div>
        )}

        <button
          type="button"
          onClick={handleCheckout}
          disabled={isSubmitting || timeLeft <= 0}
          className="w-full btn-gradient text-on-primary font-label-sm text-label-sm h-14 rounded-full flex items-center justify-center gap-2 hover:shadow-lg transition-all active:scale-[0.98] disabled:opacity-70 disabled:cursor-not-allowed"
        >
          <span className="material-symbols-outlined text-[20px]">lock</span>
          {isSubmitting ? "Processing..." : "Proceed to Checkout"}
        </button>
      </div>
    );
  }

  return (
    <div className="sticky top-28 bg-surface-container-lowest rounded-xl shadow-xl p-6 border border-outline-variant/30 flex flex-col gap-stack-md">
      <h2 className="font-section-heading text-section-heading text-on-surface">Select Tickets</h2>

      {error && (
        <div className="bg-error-container text-on-error-container p-3 rounded-lg text-sm font-medium">
          {error}
        </div>
      )}

      <div className="space-y-4">
        {tiers.map((tier) => {
          const isSelected = selectedTierId === tier.id;

          return (
            <div
              key={tier.id}
              onClick={() => setSelectedTierId(tier.id)}
              className={`rounded-xl p-4 cursor-pointer relative overflow-hidden transition-all ${
                isSelected
                  ? "border-2 border-primary bg-primary/5"
                  : "border border-outline-variant hover:border-primary/50"
              }`}
            >
              <div className="flex justify-between items-start mb-2">
                <div>
                  <h3 className="font-label-sm text-label-sm text-on-surface">{tier.tierName}</h3>
                  {tier.description && (
                    <p className="font-caption text-caption text-on-surface-variant">
                      {tier.description}
                    </p>
                  )}
                </div>
                <div className="text-right">
                  <span className="font-section-heading text-section-heading text-primary">
                    EGP {tier.basePrice.toFixed(2)}
                  </span>
                  <p className="font-caption text-caption text-on-surface-variant">
                    + EGP {fee.toFixed(2)} fee
                  </p>
                </div>
              </div>

              {isSelected && (
                <div className="flex items-center justify-between mt-4 pt-4 border-t border-outline-variant/30">
                  <span className="font-body text-body text-on-surface-variant">Quantity</span>
                  <div className="flex items-center gap-4 bg-surface rounded-full px-3 py-1 border border-outline-variant">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDecrease();
                      }}
                      disabled={quantity <= 1}
                      className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-primary transition-colors disabled:opacity-50"
                    >
                      <span className="material-symbols-outlined text-[20px]">remove</span>
                    </button>
                    <span className="font-label-sm text-label-sm text-on-surface w-4 text-center">
                      {quantity}
                    </span>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleIncrease();
                      }}
                      disabled={quantity >= 10}
                      className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-primary transition-colors disabled:opacity-50"
                    >
                      <span className="material-symbols-outlined text-[20px]">add</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>

      <div className="pt-4 border-t border-outline-variant/30">
        <div className="space-y-1.5 mb-4">
          <div className="flex justify-between text-sm">
            <span className="text-on-surface-variant">Ticket × {quantity}</span>
            <span className="text-on-surface">EGP {(price * quantity).toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-on-surface-variant">Service fee</span>
            <span className="text-on-surface">EGP {fee.toFixed(2)}</span>
          </div>
          <div className="h-px bg-outline-variant my-1" />
          <div className="flex justify-between font-semibold">
            <span className="text-on-surface">Total</span>
            <span className="text-primary font-section-heading">EGP {total.toFixed(2)}</span>
          </div>
        </div>

        <button
          type="button"
          onClick={handleReserve}
          disabled={isSubmitting || !selectedTierId}
          className="w-full btn-gradient text-on-primary font-label-sm text-label-sm h-14 rounded-full flex items-center justify-center gap-2 hover:shadow-lg transition-all active:scale-[0.98] disabled:opacity-70 disabled:cursor-not-allowed"
        >
          <span className="material-symbols-outlined text-[20px]">shopping_cart_checkout</span>
          {isSubmitting ? "Reserving..." : "Add to Cart"}
        </button>
        <p className="text-center font-caption text-caption text-on-surface-variant mt-4">
          Secure checkout powered by Eventora
        </p>
      </div>
    </div>
  );
}
