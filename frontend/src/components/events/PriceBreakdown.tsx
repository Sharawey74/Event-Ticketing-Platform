import { ArrowDown, ArrowUp } from "lucide-react";

interface PriceBreakdownProps {
  tierName: string;
  quantity: number;
  /** List price of one ticket, before any pricing rule. */
  unitPrice: number;
  /** What the server actually charges, after every rule it applied. */
  totalAmount: number;
  className?: string;
}

const egp = (value: number) =>
  `EGP ${value.toLocaleString("en-EG", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;

/**
 * The price a reservation will be charged, and why it differs from the list
 * price.
 *
 * There is no service fee, and no fee row: the charge is the tier price times
 * quantity after the server's pricing rules, and nothing is added on top.
 *
 * Both directions are shown. PricingEngine can move the price DOWN (early bird
 * at 50% inside 30 days, group at 10% for five or more) or UP (25% once a tier
 * passes 80% sold). Only the downward case used to render, so a surged booking
 * showed a total higher than the list price with nothing explaining it — which
 * reads as a bug, or worse.
 *
 * Rates are never hardcoded here. The delta is derived from the two figures the
 * server sent, so this stays correct if a constant changes.
 */
export function PriceBreakdown({
  tierName,
  quantity,
  unitPrice,
  totalAmount,
  className = "",
}: PriceBreakdownProps) {
  const listPrice = unitPrice * quantity;
  const delta = totalAmount - listPrice;
  // Guard against floating-point dust from the currency maths.
  const adjusted = Math.abs(delta) >= 0.01;
  const isSurge = delta > 0;

  return (
    <div className={`space-y-2 ${className}`}>
      <div className="flex justify-between text-sm">
        <span className="text-on-surface-variant">
          {tierName} × {quantity}
        </span>
        <span className={adjusted ? "text-on-surface-variant" : "text-on-surface"}>
          {egp(listPrice)}
        </span>
      </div>

      {adjusted && (
        <div className="flex items-start justify-between gap-3 text-sm">
          <span
            className={`inline-flex items-center gap-1.5 ${
              isSurge ? "text-on-warning-container" : "text-on-success-container"
            }`}
          >
            {isSurge ? (
              <ArrowUp className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            ) : (
              <ArrowDown className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            )}
            {isSurge ? "High demand" : "Discount applied"}
          </span>
          <span
            className={`shrink-0 font-medium ${
              isSurge ? "text-on-warning-container" : "text-on-success-container"
            }`}
          >
            {isSurge ? "+" : "−"}
            {egp(Math.abs(delta))}
          </span>
        </div>
      )}

      <div className="h-px bg-outline-variant" />

      <div className="flex justify-between font-semibold">
        <span className="text-on-surface">Total</span>
        <span className="text-lg text-primary">{egp(totalAmount)}</span>
      </div>

      {adjusted && (
        <p className="font-caption text-outline-text">
          {isSurge
            ? "This tier is nearly sold out, so its price has gone up."
            : "A pricing rule reduced this booking."}
        </p>
      )}
    </div>
  );
}
