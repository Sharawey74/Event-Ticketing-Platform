"use client";

/**
 * A drifting field of tickets behind the hero.
 *
 * Built as animated DOM rather than a video file for three reasons: it is
 * ~2 KB instead of several MB, it needs no asset shipped or hosted (the CSP
 * is `default-src 'self'`, so an external video would be blocked outright),
 * and it re-tokens with the palette instead of baking colours into pixels.
 *
 * ⚠️ Every value below is a fixed literal, never Math.random(). Randomising at
 * render time produces different markup on the server and the client, which
 * React reports as a hydration mismatch.
 */

interface Ticket {
  /** vw from the left edge. */
  left: number;
  /** Seconds for one full fall. */
  duration: number;
  /** Negative so the field starts mid-flight rather than empty. */
  delay: number;
  scale: number;
  opacity: number;
  /** Degrees of tumble over the fall. */
  spin: number;
  tint: "primary" | "secondary" | "light";
}

const TICKETS: Ticket[] = [
  { left: 4, duration: 19, delay: -2, scale: 0.75, opacity: 0.5, spin: 260, tint: "primary" },
  { left: 11, duration: 25, delay: -11, scale: 1.05, opacity: 0.32, spin: -180, tint: "light" },
  { left: 17, duration: 16, delay: -6, scale: 0.6, opacity: 0.55, spin: 320, tint: "secondary" },
  { left: 23, duration: 28, delay: -18, scale: 1.2, opacity: 0.24, spin: -140, tint: "primary" },
  { left: 30, duration: 21, delay: -4, scale: 0.85, opacity: 0.45, spin: 200, tint: "light" },
  { left: 36, duration: 17, delay: -13, scale: 0.65, opacity: 0.5, spin: -300, tint: "secondary" },
  { left: 43, duration: 24, delay: -8, scale: 1.1, opacity: 0.28, spin: 160, tint: "primary" },
  { left: 49, duration: 20, delay: -16, scale: 0.8, opacity: 0.42, spin: -240, tint: "light" },
  { left: 55, duration: 27, delay: -3, scale: 1.15, opacity: 0.26, spin: 180, tint: "secondary" },
  { left: 62, duration: 18, delay: -10, scale: 0.7, opacity: 0.52, spin: -280, tint: "primary" },
  { left: 68, duration: 23, delay: -20, scale: 0.95, opacity: 0.34, spin: 220, tint: "light" },
  { left: 74, duration: 15, delay: -7, scale: 0.55, opacity: 0.58, spin: -200, tint: "secondary" },
  { left: 80, duration: 26, delay: -14, scale: 1.25, opacity: 0.22, spin: 300, tint: "primary" },
  { left: 86, duration: 19, delay: -1, scale: 0.9, opacity: 0.44, spin: -160, tint: "light" },
  { left: 92, duration: 22, delay: -17, scale: 0.72, opacity: 0.48, spin: 240, tint: "secondary" },
  { left: 97, duration: 29, delay: -9, scale: 1.0, opacity: 0.3, spin: -220, tint: "primary" },
];

const TINTS: Record<Ticket["tint"], string> = {
  primary: "rgba(167, 118, 245, 0.85)",
  secondary: "rgba(125, 118, 245, 0.8)",
  light: "rgba(255, 255, 255, 0.75)",
};

/** A stub-and-body ticket: notch between the two halves, perforation dots on the stub. */
function TicketGlyph({ fill }: { fill: string }) {
  return (
    <svg width="46" height="26" viewBox="0 0 46 26" fill="none" aria-hidden="true">
      <path
        d="M2 5a3 3 0 0 1 3-3h36a3 3 0 0 1 3 3v3a5 5 0 0 0 0 10v3a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3v-3a5 5 0 0 0 0-10V5Z"
        fill={fill}
      />
      <circle cx="30" cy="8" r="1.1" fill="rgba(12,6,32,0.45)" />
      <circle cx="30" cy="13" r="1.1" fill="rgba(12,6,32,0.45)" />
      <circle cx="30" cy="18" r="1.1" fill="rgba(12,6,32,0.45)" />
    </svg>
  );
}

export function TicketField() {
  return (
    <div
      className="ticket-field pointer-events-none absolute inset-0 overflow-hidden"
      aria-hidden="true"
    >
      {TICKETS.map((ticket, i) => (
        <span
          key={i}
          className="ticket-fall absolute top-0 block"
          style={{
            left: `${ticket.left}%`,
            opacity: ticket.opacity,
            animationDuration: `${ticket.duration}s`,
            animationDelay: `${ticket.delay}s`,
            ["--ticket-scale" as string]: ticket.scale,
            ["--ticket-spin" as string]: `${ticket.spin}deg`,
          }}
        >
          <TicketGlyph fill={TINTS[ticket.tint]} />
        </span>
      ))}
    </div>
  );
}
