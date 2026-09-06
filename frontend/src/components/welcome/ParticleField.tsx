"use client";

import { useEffect, useRef } from "react";

/**
 * The drifting, twinkling star field on /welcome, plus the parallax that
 * shifts the light fields behind it with the cursor.
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
  parallaxFactor: number;
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
const PUSH_RADIUS = 180;

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
    const fields = container.querySelector<HTMLElement>("[data-ws-fields]");

    let width = 0;
    let height = 0;
    let particles: Particle[] = [];
    let frame = 0;

    // Pointer state. currentX/Y trail targetX/Y so the parallax eases in
    // rather than snapping to every event.
    let targetX = 0;
    let targetY = 0;
    let currentX = 0;
    let currentY = 0;
    let pointerActive = false;
    let pointerX = -1000;
    let pointerY = -1000;

    // Cached, and refreshed by ResizeObserver and scroll rather than read per
    // event. getBoundingClientRect() inside pointermove forces a layout on
    // every move, which is the standard way to ruin INP on a page like this.
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

        // Three depth bands. Nearer particles are larger, faster, and shift
        // further with the cursor, which is what reads as depth.
        const depth = Math.random();
        let parallaxFactor: number;
        let radius: number;
        let speed: number;
        if (depth < 0.45) {
          parallaxFactor = 0.035;
          radius = 0.6 + Math.random() * 0.6;
          speed = 0.75;
        } else if (depth < 0.82) {
          parallaxFactor = 0.075;
          radius = 1.1 + Math.random() * 0.8;
          speed = 1.2;
        } else {
          parallaxFactor = 0.14;
          radius = 1.6 + Math.random() * 1.0;
          speed = 1.7;
        }

        const angle = Math.random() * Math.PI * 2;
        const velocity = (0.28 + Math.random() * 0.45) * speed;

        particles.push({
          originX: baseX,
          originY: baseY,
          vx: Math.cos(angle) * velocity,
          vy: Math.sin(angle) * velocity,
          wobbleFreqX: 0.008 + Math.random() * 0.018,
          wobbleFreqY: 0.008 + Math.random() * 0.018,
          wobbleAmpX: 18 + Math.random() * 32,
          wobbleAmpY: 15 + Math.random() * 26,
          radius,
          color: PALETTE[Math.floor(Math.random() * PALETTE.length)],
          baseAlpha: 0.2 + Math.random() * 0.6,
          twinkleSpeed: 0.04 + Math.random() * 0.08,
          twinklePhase: Math.random() * Math.PI * 2,
          shimmerDepth: 0.35 + Math.random() * 0.4,
          parallaxFactor,
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
      currentX += (targetX - currentX) * 0.1;
      currentY += (targetY - currentY) * 0.1;

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

        let x = p.originX + wobbleX + currentX * p.parallaxFactor * width * 1.35;
        let y = p.originY + wobbleY + currentY * p.parallaxFactor * height * 1.35;

        // Near the cursor a particle is pushed aside and brightens, so the
        // field reads as displaced rather than merely lit.
        let extraAlpha = 0;
        let scale = 1;
        if (pointerActive) {
          const dx = x - pointerX;
          const dy = y - pointerY;
          const dist = Math.hypot(dx, dy);
          if (dist < PUSH_RADIUS && dist > 0) {
            const force = 1 - dist / PUSH_RADIUS;
            const push = force * 24 * (p.depth + 0.3);
            x += (dx / dist) * push;
            y += (dy / dist) * push;
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
      if (fields) {
        fields.style.transform = `translate3d(${currentX * 42}px, ${
          currentY * 34
        }px, 0)`;
      }
      raf = requestAnimationFrame(loop);
    };

    const onPointerMove = (event: PointerEvent) => {
      targetX = ((event.clientX - rect.left) / rect.width) * 2 - 1;
      targetY = ((event.clientY - rect.top) / rect.height) * 2 - 1;
      pointerX = event.clientX - rect.left;
      pointerY = event.clientY - rect.top;
      pointerActive = true;
    };
    const onPointerLeave = () => {
      targetX = 0;
      targetY = 0;
      pointerActive = false;
      pointerX = -1000;
      pointerY = -1000;
    };
    const onScroll = () => {
      rect = container.getBoundingClientRect();
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
      window.addEventListener("scroll", onScroll, { passive: true });
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
      window.removeEventListener("scroll", onScroll);
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
