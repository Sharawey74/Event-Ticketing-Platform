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

function shouldSkipAnimation(): boolean {
  // True during SSR too: IntersectionObserver is undefined on the server, so
  // the real figure lands in the markup rather than a zero placeholder that
  // only becomes correct once JS runs.
  if (typeof IntersectionObserver === "undefined") return true;
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

/**
 * Counts up to `value` when it scrolls into view, and again whenever `value`
 * itself changes.
 *
 * That second case is the whole reason this is not a one-shot: hero figures
 * come from the API after mount, so a counter that latched on its first render
 * would animate 0 -> 0 and then display zero forever while the real number sat
 * in the store.
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

  // Refs, not state: flipping state here would re-render, re-run the effect,
  // and its cleanup would cancel the animation frame one tick in.
  //
  // Two flags, because they answer different questions. `seenRef` is whether
  // the element has ever scrolled into view; `animatedToRef` is which value was
  // last counted to. One combined "started" flag cannot express "on screen, but
  // the number just changed".
  const seenRef = useRef(false);
  const animatedToRef = useRef<number | null>(null);

  // A lazy useState initialiser, not a ref: this has to be readable during
  // render to decide whether to skip animating, and reading a ref during render
  // is disallowed. The initialiser runs once per instance, so matchMedia is not
  // consulted on every paint.
  const [skipAnimation] = useState(shouldSkipAnimation);

  const [display, setDisplay] = useState(value);
  // Mirrors `display` for the effect only. Written inside effects, never during
  // render, so it can be read as the starting point of the next animation
  // without taking `display` as a dependency and restarting on every frame.
  const displayRef = useRef(value);

  // Adjusting state during render is React's documented way to derive from
  // props, and unlike an effect it does not schedule a second paint. Reached
  // only when nothing will be animated, so no refs are touched here.
  const [lastValue, setLastValue] = useState(value);
  if (value !== lastValue) {
    setLastValue(value);
    if (skipAnimation) setDisplay(value);
  }

  useEffect(() => {
    const node = ref.current;
    if (!node || skipAnimation) return;
    // "already finished counting to this value", not "already started".
    if (animatedToRef.current === value) return;

    const animate = (from: number) => {
      // Marked on COMPLETION, never here. Setting it at the start meant that if
      // the effect was torn down mid-flight — React re-invokes effects in dev,
      // and any dep change does the same — the cleanup cancelled the frame and
      // the re-run then saw the guard already satisfied and returned early. The
      // counter froze at whatever partial value it had reached.
      const start = performance.now();
      const tick = (now: number) => {
        // Clamp low as well as high: the first rAF callback can carry the
        // previous frame's timestamp, which predates `start` and would make
        // progress negative — rendering a negative count.
        const progress = Math.min(1, Math.max(0, (now - start) / durationMs));
        const next = from + (value - from) * EASE_OUT_CUBIC(progress);
        displayRef.current = next;
        setDisplay(next);
        if (progress < 1) {
          frameRef.current = requestAnimationFrame(tick);
        } else {
          displayRef.current = value;
          animatedToRef.current = value;
          setDisplay(value);
        }
      };
      frameRef.current = requestAnimationFrame(tick);
    };

    // Already on screen and the number changed under us — count to the new one
    // rather than waiting for an intersection that will never come again.
    if (seenRef.current) {
      animate(displayRef.current);
      return () => {
        if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
      };
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          observer.unobserve(entry.target);
          seenRef.current = true;
          animate(0);
        });
      },
      { threshold: 0.4 },
    );

    observer.observe(node);
    return () => {
      observer.disconnect();
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current);
    };
  }, [value, durationMs, skipAnimation]);

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
