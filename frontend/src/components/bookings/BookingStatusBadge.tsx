import {
  Ban,
  CheckCircle2,
  CircleAlert,
  Clock,
  Hourglass,
  RotateCcw,
  TicketCheck,
  TimerOff,
  Undo2,
  XCircle,
  type LucideIcon,
} from "lucide-react";

/**
 * All ten booking states, grouped by what they mean to the person reading:
 * actionable (something to do now), successful, refund in flight, terminal.
 *
 * Each carries its own icon as well as its colour. A shared dot conveyed
 * nothing — colour was effectively the only signal, which fails anyone who
 * cannot separate these hues.
 */
const STATUS_STYLES: Record<string, { cls: string; Icon: LucideIcon; label: string }> = {
  // Actionable — a hold is running and the user can still act.
  RESERVED: { cls: "bg-secondary-fixed text-on-secondary-fixed", Icon: Clock, label: "Reserved" },
  // Money is in flight. Warning, not error: nothing has failed yet.
  PAYMENT_PENDING: { cls: "bg-warning-container text-on-warning-container", Icon: Hourglass, label: "Payment pending" },

  // Successful.
  CONFIRMED: { cls: "bg-success-container text-on-success-container", Icon: CheckCircle2, label: "Confirmed" },
  ATTENDED: { cls: "bg-success-container text-on-success-container", Icon: TicketCheck, label: "Attended" },

  // Refund in flight — neither success nor failure, so its own voice.
  REFUND_REQUESTED: { cls: "bg-tertiary-fixed text-on-tertiary-fixed", Icon: RotateCcw, label: "Refund requested" },
  REFUND_APPROVED: { cls: "bg-primary-fixed text-on-primary-fixed", Icon: Undo2, label: "Refund approved" },

  // Terminal — muted so they recede in a long list, but still readable.
  REFUND_DENIED: { cls: "bg-surface-container-high text-on-surface-variant", Icon: XCircle, label: "Refund denied" },
  EXPIRED: { cls: "bg-surface-container-high text-on-surface-variant", Icon: TimerOff, label: "Expired" },
  CANCELLED: { cls: "bg-surface-container-high text-on-surface-variant", Icon: Ban, label: "Cancelled" },
  PAYMENT_FAILED: { cls: "bg-error-container text-on-error-container", Icon: CircleAlert, label: "Payment failed" },
};

export function BookingStatusBadge({ state }: { state: string }) {
  const entry = STATUS_STYLES[state];
  const cls = entry?.cls ?? "bg-surface-container text-on-surface-variant";
  const Icon = entry?.Icon ?? CircleAlert;
  const label = entry?.label ?? state.replace(/_/g, " ");

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${cls}`}
    >
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      {label}
    </span>
  );
}
