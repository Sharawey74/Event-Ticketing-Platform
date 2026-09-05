"use client";

import { useState } from "react";

import {
  buildEventRevenueSeries,
  formatCompact,
  type EventBar,
} from "@/lib/chart-theme";

interface SalesChartProps {
  events: { title: string; grossRevenue: number; sold: number; capacity: number }[];
}

const egp = (value: number) => `EGP ${Math.round(value).toLocaleString("en-EG")}`;

/**
 * Revenue by event.
 *
 * Horizontal bars, not a line over time. The organizer endpoint returns a
 * total per event and no dated transactions, so there is no time dimension to
 * draw — see buildEventRevenueSeries for what this replaced and why.
 *
 * Horizontal because the labels are event titles: "El Gouna Tech & Innovation
 * Summit 2026" does not fit under a vertical bar at any readable size.
 *
 * Hand-rolled rather than a charting dependency: this is one chart, and a
 * library would cost more bundle than the markup it replaces.
 */
export function SalesChart({ events }: SalesChartProps) {
  const [hovered, setHovered] = useState<string | null>(null);
  const { bars, max, total } = buildEventRevenueSeries(events);

  const hasRevenue = total > 0;

  if (bars.length === 0 || !hasRevenue) {
    return (
      <div className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-outline-variant bg-surface-container-low px-6 py-14 text-center">
        <p className="font-label-sm text-on-surface">No sales yet</p>
        <p className="max-w-sm font-caption text-on-surface-variant">
          {bars.length === 0
            ? "Publish an event and its revenue will appear here."
            : "Revenue appears here as tickets sell. Nothing has sold yet, so there is no chart to draw."}
        </p>
      </div>
    );
  }

  return (
    <div>
      <ul className="flex flex-col gap-3" role="list">
        {bars.map((bar: EventBar) => {
          const pct = max > 0 ? (bar.value / max) * 100 : 0;
          const isHovered = hovered === bar.label;
          return (
            <li
              key={bar.label}
              className="group/bar"
              onMouseEnter={() => setHovered(bar.label)}
              onMouseLeave={() => setHovered(null)}
            >
              <div className="mb-1 flex items-baseline justify-between gap-4">
                <span className="truncate font-label-sm text-on-surface" title={bar.label}>
                  {bar.label}
                </span>
                <span className="shrink-0 font-caption tabular-nums text-on-surface-variant">
                  {bar.sold.toLocaleString()} / {bar.capacity.toLocaleString()} ·{" "}
                  <span className="font-semibold text-on-surface">{egp(bar.value)}</span>
                </span>
              </div>
              <div className="h-3 w-full overflow-hidden rounded-full bg-surface-container-high">
                <div
                  className="h-full rounded-full bg-primary transition-[width,opacity] duration-500 ease-out"
                  style={{ width: `${pct}%`, opacity: isHovered ? 1 : 0.88 }}
                />
              </div>
            </li>
          );
        })}
      </ul>

      <p className="mt-4 font-caption text-outline-text">
        {/* States the total and the span, and claims no trend — there is no
            dated data behind this, so there is nothing to say about direction. */}
        {bars.length} {bars.length === 1 ? "event" : "events"} · {egp(total)} gross
        {events.length > bars.length ? ` · showing the top ${bars.length}` : ""}
      </p>

      {/* Table fallback. A bar chart alone is not readable by assistive tech,
          and the exact figures matter more here than the shape. */}
      <details className="mt-3">
        <summary className="cursor-pointer font-caption text-primary outline-none focus-visible:ring-2 focus-visible:ring-primary/50">
          View as table
        </summary>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full text-left font-caption">
            <thead>
              <tr className="border-b border-outline-variant text-on-surface-variant">
                <th scope="col" className="py-2 pr-4 font-medium">Event</th>
                <th scope="col" className="py-2 pr-4 font-medium">Sold</th>
                <th scope="col" className="py-2 font-medium">Gross</th>
              </tr>
            </thead>
            <tbody>
              {bars.map((bar) => (
                <tr key={bar.label} className="border-b border-outline-variant/50">
                  <td className="py-2 pr-4 text-on-surface">{bar.label}</td>
                  <td className="py-2 pr-4 tabular-nums text-on-surface-variant">
                    {bar.sold.toLocaleString()} / {bar.capacity.toLocaleString()}
                  </td>
                  <td className="py-2 tabular-nums text-on-surface">{formatCompact(bar.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
