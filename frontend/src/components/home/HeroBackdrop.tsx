/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useRef, useState } from "react";

import { TicketField } from "./TicketField";

const FALLBACK_IMAGE =
  "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1600&q=80";

/**
 * Where to drop the hero video. It MUST be served from our own origin:
 * `next.config.ts` sets a CSP with `default-src 'self'` and no `media-src`, so
 * media falls back to `'self'` and any external video URL is blocked outright.
 *
 * Add `frontend/public/hero.mp4` (and optionally `hero.webm`, which browsers
 * that support it will prefer) and it is picked up with no further changes.
 * Until then the still image below carries the section on its own.
 */
const VIDEO_WEBM = "/hero.webm";
const VIDEO_MP4 = "/hero.mp4";

export function HeroBackdrop() {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [videoReady, setVideoReady] = useState(false);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    // Honour the OS setting: a looping background video is exactly the kind of
    // motion this preference exists to suppress.
    const reduced =
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    if (reduced) {
      video.pause();
      return;
    }

    // Some browsers reject autoplay even when muted. That rejects a promise
    // rather than firing onError, so it needs catching separately or it
    // surfaces as an unhandled rejection in the console.
    const attempt = video.play();
    if (attempt && typeof attempt.catch === "function") {
      attempt.catch(() => setVideoReady(false));
    }
  }, []);

  return (
    <div className="absolute inset-0 overflow-hidden" aria-hidden="true">
      {/* Still layer. Always rendered — it is the poster while the video buffers
          and the entire backdrop if no video file is present. */}
      <img
        src={FALLBACK_IMAGE}
        alt=""
        className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-1000 ${
          videoReady ? "opacity-0" : "opacity-100 animate-ken-burns"
        }`}
      />

      <video
        ref={videoRef}
        className={`absolute inset-0 h-full w-full object-cover transition-opacity duration-1000 ${
          videoReady ? "opacity-100" : "opacity-0"
        }`}
        autoPlay
        muted
        loop
        playsInline
        preload="metadata"
        poster={FALLBACK_IMAGE}
        onCanPlay={() => setVideoReady(true)}
        onError={() => setVideoReady(false)}
      >
        <source src={VIDEO_WEBM} type="video/webm" />
        <source src={VIDEO_MP4} type="video/mp4" />
      </video>

      {/* Legibility scrim. Without this the headline fails contrast over the
          brighter frames of a moving video. */}
      <div className="absolute inset-0 bg-zinc-950/70" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_left,rgba(99,14,212,0.65),transparent_55%),radial-gradient(ellipse_at_bottom_right,rgba(75,65,225,0.45),transparent_50%)]" />

      {/* Tickets drift above the scrim so they stay visible, but below the copy. */}
      <TicketField />

      {/* Drifting colour blooms, so the section still feels alive on the still image.
          Kept well above the lower edge — near the bottom they read as a wash
          rather than as light. */}
      <div className="animate-pulse-glow absolute -left-24 top-1/4 h-72 w-72 rounded-full bg-primary/25 blur-[90px]" />
      <div
        className="animate-pulse-glow absolute -right-16 top-1/3 h-80 w-80 rounded-full bg-secondary/20 blur-[100px]"
        style={{ animationDelay: "2.5s" }}
      />
    </div>
  );
}
