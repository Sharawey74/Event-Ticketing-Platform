"use client";

import { useState, useEffect } from "react";
import { useRouter, usePathname } from "next/navigation";
import { Lock, Minus, Plus, ShoppingCart, Timer } from "lucide-react";

import { TicketTierResponse } from "@/types/event";
import { api } from "@/lib/api";
import { useAuthStore } from "@/store/authStore";
import { useReservationStore } from "@/store/reservationStore";
import { PriceBreakdown } from "./PriceBreakdown";

/** Mirrors BusinessConstants.MAX_TICKETS_PER_BOOKING on the server. */
const MAX_TICKETS_PER_BOOKING = 10;

/** Below this many seconds left, the hold countdown switches to a warning. */
const HOLD_WARNING_SECONDS = 60;

interface TicketTierSelectorProps {
  eventId: number;
  eventTitle: string;
  tiers: TicketTierResponse[];
}

export function TicketTierSelector({ eventId, eventTitle, tiers }: TicketTierSelectorProps) {
  const router = useRouter();
  const pathname = usePathname();
  const { token } = useAuthStore();
  const store = useReservationStore();

  const [selectedTierId, setSelectedTierId] = useState<number | null>(
    tiers.length > 0 ? tiers[0].id : null
  );
  const [quantity, setQuantity] = useState<number>(1);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Shown when the user tries to reserve while already holding a reservation for another event.
  const [showReplacePrompt, setShowReplacePrompt] = useState(false);

  // Local countdown derived from the global store's expiresAt
  const [timeLeft, setTimeLeft] = useState<number>(0);

  const selectedTier = tiers.find((t) => t.id === selectedTierId);

  // Show Order Summary only when THIS event's reservation is active in the store
  const hasActiveReservation = !!store.bookingId && store.eventId === eventId;

  useEffect(() => {
    if (!store.expiresAt || !hasActiveReservation) {
      setTimeLeft(0);
      return;
    }
    const calc = () =>
      Math.max(0, Math.floor((new Date(store.expiresAt!).getTime() - Date.now()) / 1000));
    setTimeLeft(calc());
    const interval = setInterval(() => setTimeLeft(calc()), 1000);
    return () => clearInterval(interval);
  }, [store.expiresAt, hasActiveReservation]);

  // The lower of three real limits, not a hardcoded 10: the platform cap
  // (BusinessConstants.MAX_TICKETS_PER_BOOKING), the tier's own maxPerBooking,
  // and what is actually left. Offering more than any of these produces a
  // server rejection the user could not have predicted.
  const maxQuantity = selectedTier
    ? Math.max(
        1,
        Math.min(
          MAX_TICKETS_PER_BOOKING,
          selectedTier.maxPerBooking ?? MAX_TICKETS_PER_BOOKING,
          selectedTier.availableCount,
        ),
      )
    : 1;

  // Derived rather than an effect: switching to a smaller tier must pull an
  // over-large quantity down, and doing that with setState in an effect is a
  // cascading render the lint rules rightly reject.
  const effectiveQuantity = Math.min(quantity, maxQuantity);

  const handleDecrease = () => {
    if (effectiveQuantity > 1) setQuantity(effectiveQuantity - 1);
  };

  const handleIncrease = () => {
    if (effectiveQuantity < maxQuantity) setQuantity(effectiveQuantity + 1);
  };

  const handleReserve = async () => {
    if (!token) {
      router.push("/auth");
      return;
    }
    if (!selectedTierId) return;

    // Single-reservation model: never silently overwrite a hold for another event.
    // Ask the user to explicitly replace it first.
    if (store.bookingId && store.eventId !== eventId) {
      setShowReplacePrompt(true);
      return;
    }

    await doReserve();
  };

  const doReserve = async () => {
    setIsSubmitting(true);
    setError(null);

    try {
      // Release the previously held reservation (if the user chose to replace it).
      if (store.bookingId && store.eventId !== eventId) {
        await api.delete(`/api/v1/bookings/${store.bookingId}`).catch(() => {
          // May already be pending/expired — proceed anyway
        });
        store.clear();
      }

      const idempotencyKey = crypto.randomUUID();
      const res = await api.post(
        "/api/v1/bookings",
        { eventId, tierId: selectedTierId, quantity: effectiveQuantity },
        { headers: { "Idempotency-Key": idempotencyKey } }
      );

      const booking = res.data?.data;
      if (booking && booking.bookingId) {
        store.set(
          booking.bookingId,
          booking.expiresAt,
          pathname ?? "/",
          eventTitle,
          selectedTier?.tierName ?? "",
          effectiveQuantity,
          selectedTier?.basePrice ?? 0,
          Number(booking.totalAmount ?? 0),
          eventId,
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

  const confirmReplace = async () => {
    setShowReplacePrompt(false);
    await doReserve();
  };

  const handleCheckout = async () => {
    if (!store.bookingId) return;
    setIsSubmitting(true);
    setError(null);

    try {
      const res = await api.post(`/api/bookings/${store.bookingId}/checkout`);
      const checkoutUrl = res.data?.data?.checkoutUrl;

      if (checkoutUrl) {
        // Mark the redirect as intentional so ReservationGuard doesn't block it, then leave.
        // Do NOT clear the store here — clearing wipes the flag and the booking before the
        // browser navigates; the store is reset when the user lands on confirmation/dashboard.
        store.setSkipBeforeUnload(true);
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

  if (hasActiveReservation) {
    const minutes = Math.floor(timeLeft / 60);
    const seconds = timeLeft % 60;
    const isHoldEnding = timeLeft > 0 && timeLeft <= HOLD_WARNING_SECONDS;

    return (
      <div className="sticky top-28 bg-surface-container-lowest rounded-xl shadow-xl p-6 border border-outline-variant/30 flex flex-col gap-stack-md">
        <h2 className="font-section-heading text-section-heading text-on-surface">Order Summary</h2>

        {error && (
          <div className="bg-error-container text-on-error-container p-3 rounded-lg text-sm font-medium">
            {error}
          </div>
        )}

        <PriceBreakdown
          tierName={store.tierName ?? "Ticket"}
          quantity={store.quantity}
          unitPrice={store.unitPrice}
          totalAmount={store.totalAmount}
          className="mb-4 rounded-xl border border-outline-variant/30 bg-surface-container-low p-4"
        />

        {timeLeft > 0 ? (
          <div
            className={`mb-4 flex items-center gap-3 rounded-xl border p-4 ${
              isHoldEnding
                ? "border-warning/30 bg-warning-container"
                : "border-primary/20 bg-primary/10"
            }`}
          >
            <Timer
              className={`h-5 w-5 shrink-0 ${
                isHoldEnding ? "text-on-warning-container" : "text-primary"
              }`}
              aria-hidden="true"
            />
            <div>
              <p
                className={`font-label-sm text-label-sm ${
                  isHoldEnding ? "text-on-warning-container" : "text-primary"
                }`}
              >
                {isHoldEnding ? "Hold expiring" : "Reservation held"}
              </p>
              {/* The remaining time is always text, never only a colour or a
                  ring — it has to be readable under reduced motion too. */}
              <p
                className={`font-caption text-caption ${
                  isHoldEnding ? "text-on-warning-container" : "text-on-surface-variant"
                }`}
                aria-live={isHoldEnding ? "polite" : "off"}
              >
                {minutes}:{seconds.toString().padStart(2, "0")} left to complete checkout
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
          <Lock className="h-[20px] w-[20px]" aria-hidden="true" />
          {isSubmitting ? "Processing..." : "Proceed to Checkout"}
        </button>
      </div>
    );
  }

  const displayPrice = selectedTier ? selectedTier.basePrice : 0;

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
                </div>
              </div>

              {isSelected && (
                <div className="flex items-center justify-between mt-4 pt-4 border-t border-outline-variant/30">
                  <span className="font-body text-body text-on-surface-variant">Quantity</span>
                  <div className="flex items-center gap-4 bg-surface rounded-full px-3 py-1 border border-outline-variant">
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); handleDecrease(); }}
                      disabled={effectiveQuantity <= 1}
                      className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-primary transition-colors disabled:opacity-50"
                    >
                      <Minus className="h-[20px] w-[20px]" aria-hidden="true" />
                    </button>
                    <span className="font-label-sm text-label-sm text-on-surface w-4 text-center">
                      {effectiveQuantity}
                    </span>
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); handleIncrease(); }}
                      disabled={effectiveQuantity >= maxQuantity}
                      className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-primary transition-colors disabled:opacity-50"
                    >
                      <Plus className="h-[20px] w-[20px]" aria-hidden="true" />
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
            <span className="text-on-surface-variant">Ticket × {effectiveQuantity}</span>
            <span className="text-on-surface">EGP {(displayPrice * effectiveQuantity).toFixed(2)}</span>
          </div>
          <div className="h-px bg-outline-variant my-1" />
          <div className="flex justify-between font-semibold">
            <span className="text-on-surface">Estimated Total</span>
            <span className="text-primary font-section-heading">EGP {(displayPrice * effectiveQuantity).toFixed(2)}</span>
          </div>
        </div>

        {showReplacePrompt && (
          <div className="bg-surface-container-low border border-outline-variant rounded-xl p-4 mb-4">
            <p className="font-label-sm text-label-sm text-on-surface mb-1">Replace current reservation?</p>
            <p className="font-caption text-caption text-on-surface-variant mb-3">
              You already hold a reservation for <span className="font-semibold">{store.eventTitle}</span>.
              Replacing it will release those seats.
            </p>
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowReplacePrompt(false)}
                disabled={isSubmitting}
                className="flex-1 h-11 rounded-full border border-outline-variant text-on-surface font-label-sm text-label-sm hover:bg-surface-container transition-colors disabled:opacity-60"
              >
                Keep current
              </button>
              <button
                type="button"
                onClick={confirmReplace}
                disabled={isSubmitting}
                className="flex-1 h-11 rounded-full btn-gradient text-on-primary font-label-sm text-label-sm hover:shadow-lg transition-all disabled:opacity-60"
              >
                {isSubmitting ? "Replacing..." : "Replace"}
              </button>
            </div>
          </div>
        )}

        <button
          type="button"
          onClick={handleReserve}
          disabled={isSubmitting || !selectedTierId || showReplacePrompt}
          className="w-full btn-gradient text-on-primary font-label-sm text-label-sm h-14 rounded-full flex items-center justify-center gap-2 hover:shadow-lg transition-all active:scale-[0.98] disabled:opacity-70 disabled:cursor-not-allowed"
        >
          <ShoppingCart className="h-[20px] w-[20px]" aria-hidden="true" />
          {isSubmitting ? "Reserving..." : "Add to Cart"}
        </button>
        <p className="text-center font-caption text-caption text-on-surface-variant mt-4">
          Final price calculated at checkout
        </p>
      </div>
    </div>
  );
}
