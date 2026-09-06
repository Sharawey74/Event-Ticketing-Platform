"use client";

import { useEffect, useRef } from "react";

/**
 * The drifting, twinkling star field on /welcome.
 *
 * Nothing here moves with the cursor. The reference shifted the light fields
 * by up to 42px and slid the nearest particles by up to 272px, which made the
 * whole scene appear to swim as the pointer crossed it. The only thing left of
 * that is a brightening near the cursor: the field responds, it does not
 * relocate.
 *
 * Canvas rather than DOM: this is several hundred moving dots, and that many
 * elements each with their own transform is a compositor layer count no
 * browser is happy about. One canvas is one layer.
 *
 * Every particle is seeded with Math.random(), which is safe only because the
 * seeding happens in an effect. Randomising during render would produce
 * different markup on the server and the client — the hydration mismatch that
 * TicketField carries a warning about.
 */

interface Particle {
  originX: number;
  originY: number;
  vx: number;
  vy: number;
  wobbleFreqX: number;
  wobbleFreqY: number;
  wobbleAmpX: number;
  wobbleAmpY: number;
  radius: number;
  color: string;
  baseAlpha: number;
  twinkleSpeed: number;
  twinklePhase: number;
  shimmerDepth: number;
  depth: number;
}

/** Trailing "(" is left open so the alpha can be appended without a join. */
const PALETTE = [
  "rgba(255, 255, 255,",
  "rgba(245, 238, 255,",
  "rgba(238, 218, 255,",
  "rgba(224, 185, 255,",
  "rgba(205, 140, 255,",
  "rgba(248, 180, 255,",
  "rgba(175, 195, 255,",
];

/** Tuned for a ~1440x900 desktop and scaled by area from there. */
const REFERENCE_AREA = 1440 * 900;
const REFERENCE_COUNT = 320;
const MIN_COUNT = 90;
/** How close the cursor has to be for a particle to light up. */
const GLOW_RADIUS = 180;

/**
 * Pace of the field, as fractions of the reference implementation.
 *
 * The reference moved fast enough to read as weather rather than as a night
 * sky, and drifting motion behind body copy competes with reading it. Split
 * into three because they are three different sensations: DRIFT is how fast a
 * point crosses the screen, WOBBLE how much it wanders on the way, TWINKLE how
 * quickly it brightens and dims.
 */
const DRIFT_SPEED = 0.35;
const WOBBLE_SPEED = 0.5;
const TWINKLE_SPEED = 0.55;

export function ParticleField({ containerId }: { containerId: string }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = document.getElementById(containerId);
    if (!canvas || !container) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const calm = window.matchMedia("(prefers-reduced-motion: reduce)");
    const fine = window.matchMedia("(hover: hover) and (pointer: fine)");

    let width = 0;
    let height = 0;
    let particles: Particle[] = [];
    let frame = 0;

    // Pointer position only, in scene coordinates. There is no smoothed
    // target to trail any more, because nothing is being moved.
    let pointerActive = false;
    let pointerX = -1000;
    let pointerY = -1000;

    // Cached and refreshed by ResizeObserver rather than read per event.
    // getBoundingClientRect() inside pointermove forces a layout on every
    // move, which is the standard way to ruin INP on a page like this. No
    // scroll listener alongside it: the scene is pinned to the viewport, so
    // its top never moves.
    let rect = container.getBoundingClientRect();

    const seed = () => {
      const count = Math.max(
        MIN_COUNT,
        Math.round(
          REFERENCE_COUNT * Math.min(1, (width * height) / REFERENCE_AREA),
        ),
      );
      particles = [];
      for (let i = 0; i < count; i += 1) {
        // Most of the field follows a diagonal stream from the top right down
        // to the bottom left; the rest is scattered, so it does not read as a
        // single band.
        let baseX: number;
        let baseY: number;
        if (Math.random() < 0.72) {
          const t = Math.random();
          baseX =
            (1 - t) * (width * 0.95) +
            t * (width * 0.05) +
            (Math.random() - 0.5) * (width * 0.5);
          baseY =
            (1 - t) * (height * 0.1) +
            t * (height * 0.88) +
            (Math.random() - 0.5) * (height * 0.44);
        } else {
          baseX = Math.random() * width;
          baseY = Math.random() * height;
        }

        // Three depth bands. Nearer particles are larger and drift faster,
        // which is what still reads as depth now that the cursor no longer
        // separates them.
        const depth = Math.random();
        let radius: number;
        let speed: number;
        if (depth < 0.45) {
          radius = 0.6 + Math.random() * 0.6;
          speed = 0.75;
        } else if (depth < 0.82) {
          radius = 1.1 + Math.random() * 0.8;
          speed = 1.2;
        } else {
          radius = 1.6 + Math.random() * 1.0;
          speed = 1.7;
        }

        const angle = Math.random() * Math.PI * 2;
        const velocity = (0.28 + Math.random() * 0.45) * speed * DRIFT_SPEED;

        particles.push({
          originX: baseX,
          originY: baseY,
          vx: Math.cos(angle) * velocity,
          vy: Math.sin(angle) * velocity,
          wobbleFreqX: (0.008 + Math.random() * 0.018) * WOBBLE_SPEED,
          wobbleFreqY: (0.008 + Math.random() * 0.018) * WOBBLE_SPEED,
          wobbleAmpX: 18 + Math.random() * 32,
          wobbleAmpY: 15 + Math.random() * 26,
          radius,
          color: PALETTE[Math.floor(Math.random() * PALETTE.length)],
          baseAlpha: 0.2 + Math.random() * 0.6,
          twinkleSpeed: (0.04 + Math.random() * 0.08) * TWINKLE_SPEED,
          twinklePhase: Math.random() * Math.PI * 2,
          shimmerDepth: 0.35 + Math.random() * 0.4,
          depth,
        });
      }
    };

    const measure = () => {
      rect = container.getBoundingClientRect();
      width = rect.width;
      height = rect.height;
      // Capped at 2: beyond that the pixel count grows for no visible gain on
      // dots this small.
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      seed();
    };

    const draw = (animate: boolean) => {
      frame += 1;
      ctx.clearRect(0, 0, width, height);

      for (const p of particles) {
        if (animate) {
          p.originX += p.vx;
          p.originY += p.vy;
          // Wrap rather than bounce, so the field never thins at an edge.
          if (p.originX < -40) p.originX = width + 40;
          if (p.originX > width + 40) p.originX = -40;
          if (p.originY < -40) p.originY = height + 40;
          if (p.originY > height + 40) p.originY = -40;
          p.twinklePhase += p.twinkleSpeed;
        }

        const wobbleX =
          Math.sin(frame * p.wobbleFreqX + p.twinklePhase) * p.wobbleAmpX;
        const wobbleY =
          Math.cos(frame * p.wobbleFreqY + p.twinklePhase) * p.wobbleAmpY;

        const x = p.originX + wobbleX;
        const y = p.originY + wobbleY;

        // Near the cursor a particle brightens and swells in place. The
        // reference also shoved it aside; that is the part that read as the
        // page moving, so only the light is left.
        let extraAlpha = 0;
        let scale = 1;
        if (pointerActive) {
          const dist = Math.hypot(x - pointerX, y - pointerY);
          if (dist < GLOW_RADIUS) {
            const force = 1 - dist / GLOW_RADIUS;
            extraAlpha = force * 0.55;
            scale = 1 + force * 0.5;
          }
        }

        const twinkle = Math.sin(p.twinklePhase) * p.shimmerDepth;
        const alpha = Math.min(
          Math.max(p.baseAlpha + twinkle + extraAlpha, 0.08),
          1,
        );
        const r = p.radius * scale;

        ctx.beginPath();
        ctx.arc(x, y, r, 0, Math.PI * 2);
        ctx.fillStyle = `${p.color}${alpha.toFixed(3)})`;
        ctx.fill();

        // A halo only on the foreground band or at the peak of a twinkle. On
        // every particle it washes the whole field out.
        if (p.depth > 0.75 || alpha > 0.82) {
          ctx.beginPath();
          ctx.arc(x, y, r * 2.8, 0, Math.PI * 2);
          ctx.fillStyle = `${p.color}${(alpha * 0.28).toFixed(3)})`;
          ctx.fill();
        }
      }
    };

    let raf = 0;
    const loop = () => {
      draw(true);
      raf = requestAnimationFrame(loop);
    };

    const onPointerMove = (event: PointerEvent) => {
      pointerX = event.clientX - rect.left;
      pointerY = event.clientY - rect.top;
      pointerActive = true;
    };
    const onPointerLeave = () => {
      pointerActive = false;
      pointerX = -1000;
      pointerY = -1000;
    };
    measure();

    // Reduced motion gets the field once and nothing else: no loop, no
    // listeners, and no rAF holding a frame open for the life of the page.
    if (calm.matches) {
      draw(false);
      return;
    }

    const observer = new ResizeObserver(measure);
    observer.observe(container);

    if (fine.matches) {
      container.addEventListener("pointermove", onPointerMove, {
        passive: true,
      });
      container.addEventListener("pointerleave", onPointerLeave, {
        passive: true,
      });
    }

    raf = requestAnimationFrame(loop);

    return () => {
      // The reference implementation never cancelled its rAF. In a single-page
      // app that keeps drawing to a detached canvas for the rest of the
      // session, once per visit to this route.
      cancelAnimationFrame(raf);
      observer.disconnect();
      container.removeEventListener("pointermove", onPointerMove);
      container.removeEventListener("pointerleave", onPointerLeave);
    };
  }, [containerId]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 z-[2] h-full w-full"
    />
  );
}
