"use client";

import { useEffect, useRef, useState } from "react";

interface AnimatedCounterProps {
  /** The number to count up to. */
  value: number;
  /** Rendered before the number, e.g. "EGP ". */
  prefix?: string;
  /** Rendered after the number, e.g. "+" or "%". */
  suffix?: string;
  decimals?: number;
  durationMs?: number;
  className?: string;
}

const EASE_OUT_CUBIC = (t: number) => 1 - Math.pow(1 - t, 3);

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Counts up to `value` the first time it scrolls into view.
 *
 * The final value is rendered immediately — not zero — whenever animating would
 * be wrong or impossible: reduced motion, no IntersectionObserver, or during
 * SSR. That keeps the real figure in the markup rather than a placeholder that
 * only becomes correct once JS runs.
 */
export function AnimatedCounter({
  value,
  prefix = "",
  suffix = "",
  decimals = 0,
  durationMs = 1400,
  className = "",
}: AnimatedCounterProps) {
  const ref = useRef<HTMLSpanElement | null>(null);
  const frameRef = useRef<number | null>(null);
  // A ref, not state: flipping state here would re-render, re-run the effect,
  // and its cleanup would cancel the animation frame one tick in.
  const startedRef = useRef(false);
  const [display, setDisplay] = useState(value);

  useEffect(() => {
    const node = ref.current;
    if (!node || startedRef.current) return;

    // No setState needed: `display` is already initialised to `value`, so the
    // final figure is what renders when animating is off or unavailable.
    if (prefersReducedMotion() || typeof IntersectionObserver === "undefined") {
      startedRef.current = true;
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting || startedRef.current) return;
          observer.unobserve(entry.target);
          startedRef.current = true;

          const start = performance.now();
          const tick = (now: number) => {
            // Clamp low as well as high: the first rAF callback can carry the
            // previous frame's timestamp, which predates `start` and would make
            // progress negative — rendering a negative count.
            const progress = Math.min(1, Math.max(0, (now - start) / durationMs));
            setDisplay(value * EASE_OUT_CUBIC(progress));
            if (progress < 1) {
              frameRef.current = requestAnimationFrame(tick);
            } else {
              setDisplay(value);
            }
          };
          setDisplay(0);
          frameRef.current = requestAnimationFrame(tick);
        });
      },
      { threshold: 0.4 },
    );

    observer.observe(node);
    return () => {
      observer.disconnect();
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
    };
  }, [value, durationMs]);

  return (
    <span ref={ref} className={className}>
      {prefix}
      {display.toLocaleString(undefined, {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
      })}
      {suffix}
    </span>
  );
}
