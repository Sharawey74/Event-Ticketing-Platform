/**
 * Chart colour tokens.
 *
 * SVG `stroke`/`fill` and charting libraries need literal colour values, so the
 * goal here is not "zero literals" — it is *one place* to change them. These
 * mirror the `--color-*` tokens in globals.css; keep the two in step.
 */
export const chartTheme = {
  /** --color-primary */
  line: "#630ed4",
  /** --color-secondary */
  lineAlt: "#4b41e1",
  /** --color-surface-container-lowest — dot centres, so they read as cut-outs */
  dot: "#ffffff",
  /** --color-surface-container-highest */
  grid: "#e0e3e5",
  /** --color-outline */
  axisText: "#7b7487",
} as const;

export interface EventBar {
  /** Event title, rendered as the bar's label. */
  label: string;
  /** Real gross revenue for that event, straight from the API. */
  value: number;
  /** Tickets sold, shown alongside the money. */
  sold: number;
  capacity: number;
}

export interface EventSeries {
  bars: EventBar[];
  /** Rounded-up axis maximum, so gridlines land on readable numbers. */
  max: number;
  total: number;
}

/** Rounds up to 1/2/5 x 10^n so the axis reads 10k rather than 9,847. */
function niceCeiling(value: number): number {
  if (value <= 0) return 100;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const normalised = value / magnitude;
  const step = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10;
  return step * magnitude;
}

/** Compact axis/tooltip formatting: 8250 -> "8.3k". */
export function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}k`;
  return Math.round(value).toString();
}

/**
 * One bar per event, from the figures the organizer endpoint actually returns.
 *
 * This replaces a synthetic daily series. That version took each event's total
 * gross revenue and spread it across thirty invented buckets with a weighting
 * curve ("sales accelerate as the event approaches"), then rendered it as
 * "Sales over the last 30 days" with per-day hover values. The total was real;
 * every point on the curve was not, and an organizer reading revenue for a
 * given day off that chart would have been reading a number nobody earned.
 *
 * The API returns id, title, date, status, sold, capacity and grossRevenue per
 * event — and `date` is when the event happens, not when a ticket sold. There
 * is no time dimension to plot, so this does not plot one. A real daily series
 * needs a backend endpoint that does not exist yet.
 *
 * Sorted by revenue so the ranking is the message, and capped so a long roster
 * stays readable.
 */
export function buildEventRevenueSeries(
  events: { title: string; grossRevenue: number; sold: number; capacity: number }[],
  limit = 8,
): EventSeries {
  const bars = events
    .map((event) => ({
      label: event.title,
      value: event.grossRevenue || 0,
      sold: event.sold || 0,
      capacity: event.capacity || 0,
    }))
    .sort((a, b) => b.value - a.value)
    .slice(0, limit);

  const peak = bars.reduce((max, bar) => Math.max(max, bar.value), 0);
  const total = events.reduce((sum, event) => sum + (event.grossRevenue || 0), 0);

  return { bars, max: niceCeiling(peak) || 100, total };
}
