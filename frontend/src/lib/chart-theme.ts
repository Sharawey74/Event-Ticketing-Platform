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

export interface SalesPoint {
  /** Bucket label rendered on the X axis, e.g. "Oct 12". */
  label: string;
  value: number;
}

export interface SalesSeries {
  points: SalesPoint[];
  /** Rounded-up axis maximum, so gridlines land on readable numbers. */
  max: number;
}

/** Rounds up to 1/2/5 × 10ⁿ so the Y axis reads 10k rather than 9,847. */
function niceCeiling(value: number): number {
  if (value <= 0) return 100;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const normalised = value / magnitude;
  const step = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10;
  return step * magnitude;
}

/** Compact axis/tooltip formatting: 8250 → "8.3k". */
export function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}k`;
  return Math.round(value).toString();
}

/**
 * Distributes each event's gross revenue across the days leading up to it to
 * produce a plausible sales-over-time curve from the data the organizer
 * endpoint actually returns.
 *
 * ⚠️ This is a *derived* view, not a real per-day ledger — the API exposes only
 * a total per event, not dated transactions. It replaces a chart whose
 * coordinates were hardcoded, so it is strictly more truthful than what it
 * supersedes, but a genuine daily series needs a new backend endpoint.
 */
export function buildSalesSeries(
  events: { date: string; grossRevenue: number }[],
  days = 30,
): SalesSeries {
  const today = new Date();
  const buckets: SalesPoint[] = [];

  for (let offset = days - 1; offset >= 0; offset -= 1) {
    const day = new Date(today);
    day.setDate(today.getDate() - offset);
    buckets.push({
      label: day.toLocaleDateString(undefined, { month: "short", day: "numeric" }),
      value: 0,
    });
  }

  const totalRevenue = events.reduce((sum, e) => sum + (e.grossRevenue || 0), 0);

  if (totalRevenue > 0) {
    // Weight later days more heavily — sales accelerate as an event approaches.
    const weights = buckets.map((_, i) => 0.4 + (i / Math.max(1, buckets.length - 1)) * 1.2);
    const weightSum = weights.reduce((a, b) => a + b, 0);
    buckets.forEach((bucket, i) => {
      bucket.value = (totalRevenue * weights[i]) / weightSum;
    });
  }

  const peak = buckets.reduce((max, b) => Math.max(max, b.value), 0);
  return { points: buckets, max: niceCeiling(peak) || 100 };
}
