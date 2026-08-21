"use client";

import { useMemo, useState } from "react";

import {
  buildSalesSeries,
  chartTheme,
  formatCompact,
  type SalesPoint,
} from "@/lib/chart-theme";

const VIEW_W = 800;
const VIEW_H = 300;
const PAD_L = 56;
const PAD_R = 16;
const PAD_T = 20;
const PAD_B = 34;

interface SalesChartProps {
  events: { date: string; grossRevenue: number }[];
}

/**
 * Sales-over-time area chart.
 *
 * Hand-rolled SVG rather than a charting dependency: this is one chart, and the
 * library would cost more bundle than the ~120 lines it replaces. The line
 * draws itself on mount and the nearest point tracks the pointer.
 */
export function SalesChart({ events }: SalesChartProps) {
  const [hover, setHover] = useState<{ point: SalesPoint; x: number; y: number } | null>(null);

  const { points, max } = useMemo(() => buildSalesSeries(events), [events]);

  const plotW = VIEW_W - PAD_L - PAD_R;
  const plotH = VIEW_H - PAD_T - PAD_B;

  const coords = useMemo(
    () =>
      points.map((point, i) => ({
        point,
        x: PAD_L + (i / Math.max(1, points.length - 1)) * plotW,
        y: PAD_T + plotH - (point.value / max) * plotH,
      })),
    [points, max, plotW, plotH],
  );

  // Catmull-Rom style smoothing — a straight polyline reads as jagged at this
  // density, and a cubic through every point is smoother than midpoint curves.
  const linePath = useMemo(() => {
    if (coords.length < 2) return "";
    let d = `M ${coords[0].x} ${coords[0].y}`;
    for (let i = 0; i < coords.length - 1; i += 1) {
      const curr = coords[i];
      const next = coords[i + 1];
      const cx = (curr.x + next.x) / 2;
      d += ` C ${cx} ${curr.y}, ${cx} ${next.y}, ${next.x} ${next.y}`;
    }
    return d;
  }, [coords]);

  const areaPath = useMemo(() => {
    if (!linePath) return "";
    const last = coords[coords.length - 1];
    const first = coords[0];
    return `${linePath} L ${last.x} ${PAD_T + plotH} L ${first.x} ${PAD_T + plotH} Z`;
  }, [linePath, coords, plotH]);

  const gridValues = [max, max * 0.75, max * 0.5, max * 0.25, 0];

  // A flat line pinned to zero reads as a broken chart rather than as "nothing
  // has sold yet", so say so instead of drawing it.
  const hasRevenue = points.some((p) => p.value > 0);

  if (!hasRevenue) {
    return (
      <div className="mt-4 flex h-[300px] w-full flex-col items-center justify-center rounded-lg border border-dashed border-outline-variant/60 text-center">
        <span className="material-symbols-outlined mb-3 text-[40px] text-surface-container-highest">
          show_chart
        </span>
        <p className="font-body-lg font-bold text-on-surface">No sales yet</p>
        <p className="font-body mt-1 max-w-xs text-on-surface-variant">
          Once tickets start selling, revenue for the last 30 days appears here.
        </p>
      </div>
    );
  }

  function handleMove(e: React.MouseEvent<SVGSVGElement>) {
    const rect = e.currentTarget.getBoundingClientRect();
    const ratio = VIEW_W / rect.width;
    const localX = (e.clientX - rect.left) * ratio;
    let nearest = coords[0];
    for (const c of coords) {
      if (Math.abs(c.x - localX) < Math.abs(nearest.x - localX)) nearest = c;
    }
    setHover(nearest ? { point: nearest.point, x: nearest.x, y: nearest.y } : null);
  }

  return (
    <div className="relative mt-4 h-[300px] w-full">
      <svg
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        className="h-full w-full"
        onMouseMove={handleMove}
        onMouseLeave={() => setHover(null)}
        role="img"
        aria-label={`Sales over the last ${points.length} days, peaking at EGP ${formatCompact(max)}`}
      >
        <defs>
          <linearGradient id="salesArea" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={chartTheme.line} stopOpacity="0.28" />
            <stop offset="100%" stopColor={chartTheme.line} stopOpacity="0" />
          </linearGradient>
          <linearGradient id="salesLine" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor={chartTheme.line} />
            <stop offset="100%" stopColor={chartTheme.lineAlt} />
          </linearGradient>
        </defs>

        {gridValues.map((value, i) => {
          const y = PAD_T + plotH - (value / max) * plotH;
          return (
            <g key={value}>
              <line
                x1={PAD_L}
                y1={y}
                x2={VIEW_W - PAD_R}
                y2={y}
                stroke={chartTheme.grid}
                strokeWidth="1"
                strokeDasharray={i === gridValues.length - 1 ? undefined : "4,4"}
              />
              <text
                x={PAD_L - 10}
                y={y + 4}
                fontSize="11"
                fill={chartTheme.axisText}
                textAnchor="end"
              >
                EGP {formatCompact(value)}
              </text>
            </g>
          );
        })}

        {[0, Math.floor(coords.length / 3), Math.floor((coords.length * 2) / 3), coords.length - 1]
          .filter((i, idx, arr) => coords[i] && arr.indexOf(i) === idx)
          .map((i) => (
            <text
              key={i}
              x={coords[i].x}
              y={VIEW_H - 10}
              fontSize="11"
              fill={chartTheme.axisText}
              textAnchor="middle"
            >
              {coords[i].point.label}
            </text>
          ))}

        <path d={areaPath} fill="url(#salesArea)" className="animate-fade-in" />
        <path
          d={linePath}
          fill="none"
          stroke="url(#salesLine)"
          strokeWidth="3.5"
          strokeLinecap="round"
          pathLength={1}
          className="animate-draw"
        />

        {hover && (
          <g className="pointer-events-none">
            <line
              x1={hover.x}
              y1={PAD_T}
              x2={hover.x}
              y2={PAD_T + plotH}
              stroke={chartTheme.line}
              strokeWidth="1"
              strokeDasharray="3,3"
              opacity="0.5"
            />
            <circle
              cx={hover.x}
              cy={hover.y}
              r="7"
              fill={chartTheme.dot}
              stroke={chartTheme.line}
              strokeWidth="3.5"
            />
          </g>
        )}
      </svg>

      {hover && (
        <div
          className="animate-scale-in pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-[140%] rounded-lg bg-inverse-surface px-3 py-1.5 shadow-lg"
          style={{ left: `${(hover.x / VIEW_W) * 100}%`, top: `${(hover.y / VIEW_H) * 100}%` }}
        >
          <p className="font-caption whitespace-nowrap text-inverse-on-surface">
            {hover.point.label}
          </p>
          <p className="font-label-sm whitespace-nowrap text-inverse-on-surface">
            EGP{" "}
            {hover.point.value.toLocaleString(undefined, {
              minimumFractionDigits: 0,
              maximumFractionDigits: 0,
            })}
          </p>
        </div>
      )}
    </div>
  );
}
