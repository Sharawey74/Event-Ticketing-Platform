"use client";

import { useEffect, useRef } from "react";

import { TicketField } from "./TicketField";

/**
 * The hero backdrop: a drifting violet/indigo aurora with a pointer-tracking
 * highlight, over a near-black ground.
 *
 * Replaces the previous video-plus-photo backdrop. That version requested
 * `/hero.mp4` and `/hero.webm`, neither of which was ever committed, so every
 * page load produced two 404s and fell through to a remote Unsplash still —
 * a third-party DNS lookup, TLS handshake and image download sitting directly
 * on the LCP path, for decoration.
 *
 * Everything here is CSS. See the AURORA HERO block in globals.css for why the
 * blur lives on the container and why the drift durations are prime-ish.
 */
export function AuroraBackdrop() {
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;

    // Touch and reduced-motion users get the static aurora. Matching the CSS
    // media queries here means we never attach a listener they cannot use.
    const fine = window.matchMedia("(hover: hover) and (pointer: fine)");
    const calm = window.matchMedia("(prefers-reduced-motion: reduce)");
    if (!fine.matches || calm.matches) return;

    // The rect is cached and refreshed by ResizeObserver rather than read in
    // the handler. getBoundingClientRect() inside pointermove forces a layout
    // on every event, which is the classic way to wreck INP on a hero.
    let rect = root.getBoundingClientRect();
    const ro = new ResizeObserver(() => {
      rect = root.getBoundingClientRect();
    });
    ro.observe(root);

    const onScroll = () => {
      rect = root.getBoundingClientRect();
    };

    const onMove = (event: PointerEvent) => {
      const x = ((event.clientX - rect.left) / rect.width) * 100;
      const y = ((event.clientY - rect.top) / rect.height) * 100;
      // Only custom-property writes. No reads, no layout.
      root.style.setProperty("--hx", `${x.toFixed(2)}%`);
      root.style.setProperty("--hy", `${y.toFixed(2)}%`);
      root.style.setProperty("--h-alpha", "0.28");
    };

    const onLeave = () => root.style.setProperty("--h-alpha", "0");

    root.addEventListener("pointermove", onMove, { passive: true });
    root.addEventListener("pointerleave", onLeave, { passive: true });
    window.addEventListener("scroll", onScroll, { passive: true });

    return () => {
      ro.disconnect();
      root.removeEventListener("pointermove", onMove);
      root.removeEventListener("pointerleave", onLeave);
      window.removeEventListener("scroll", onScroll);
    };
  }, []);

  return (
    <div
      ref={rootRef}
      className="absolute inset-0 overflow-hidden bg-[var(--night-000)]"
      aria-hidden="true"
    >
      <div className="aurora">
        <div
          className="aurora-blob aurora-a left-[-10%] top-[-15%] h-[70%] w-[55%]"
          style={{ background: "var(--violet-600)" }}
        />
        <div
          className="aurora-blob aurora-b right-[-12%] top-[-10%] h-[75%] w-[50%]"
          style={{ background: "var(--indigo-600)" }}
        />
        <div
          className="aurora-blob aurora-c bottom-[-25%] left-[18%] h-[70%] w-[60%]"
          style={{ background: "var(--violet-500)" }}
        />
        <div
          className="aurora-blob aurora-d right-[8%] bottom-[-20%] h-[55%] w-[42%]"
          style={{ background: "var(--indigo-500)" }}
        />
      </div>

      <div className="aurora-pointer" />

      {/* Brand signature. Sits above the aurora so the tickets read, below the
          copy so they never compete with it. */}
      <TicketField />

      {/* Legibility floor. The aurora drifts, so the headline needs a constant
          minimum contrast rather than one that depends on blob position. */}
      <div className="absolute inset-0 bg-[var(--night-000)]/45" />
      <div className="absolute inset-x-0 bottom-0 h-1/3 bg-linear-to-t from-[var(--night-000)]/80 to-transparent" />
    </div>
  );
}
