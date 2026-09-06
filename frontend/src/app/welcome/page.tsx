"use client";

import Image from "next/image";
import Link from "next/link";
import { ArrowRight } from "lucide-react";

import { ParticleField } from "@/components/welcome/ParticleField";

const SCENE_ID = "welcome-scene";

/**
 * A standalone entry page: full-bleed midnight scene, a minimal bar, and one
 * button through to the home page.
 *
 * Locked to the viewport: exactly 100svh, both overflow axes pinned, and no
 * shared footer (SiteFooter suppresses itself here). `overflow-x-hidden` alone
 * was not enough — a container that is not `visible` on one axis computes to
 * `auto` on the other, so the scene had quietly become its own scroll
 * container. Same rule that put a stray scrollbar on the featured rail.
 *
 * Nothing redirects here and nothing is stored to remember a visit — it is a
 * page you link to deliberately, not a gate in front of `/`.
 *
 * Fixed-dark in both themes, by design. Every colour here is either white or a
 * scene-local value, never a semantic token, so the dark-mode swap cannot pull
 * the ground out from under white text.
 *
 * Two departures from the reference, both because the reference is a mockup:
 * the brand mark is the real asset rather than a CSS circle with a rotated
 * ring, and every link goes to a route that exists. The reference bar was
 * decorative; dead nav is worse than no nav.
 */
export default function WelcomePage() {
  return (
    <div
      id={SCENE_ID}
      className="welcome-scene relative flex h-[100svh] w-full select-none flex-col justify-between overflow-hidden overscroll-none text-white"
    >
      {/* Light fields. The wrapper is what the pointer parallax translates —
          moving one element rather than three keeps it to a single transform
          per frame. */}
      <div className="ws-fields" data-ws-fields aria-hidden="true">
        <div className="ws-blob ws-blob-purple -top-[15%] left-[12%] h-[70vw] max-h-[920px] w-[70vw] max-w-[1050px] opacity-95 blur-[90px] md:blur-[135px]" />
        <div className="ws-blob ws-blob-magenta left-[28%] top-[5%] h-[65vw] max-h-[880px] w-[72vw] max-w-[1100px] opacity-90 blur-[105px] md:blur-[150px]" />
        <div className="ws-blob ws-blob-indigo -bottom-[20%] left-[20%] h-[60vw] max-h-[820px] w-[78vw] max-w-[1180px] opacity-90 blur-[95px] md:blur-[140px]" />
        <div className="ws-vignette" />
      </div>

      <ParticleField containerId={SCENE_ID} />

      {/* Bar */}
      <header className="ws-header relative z-10 flex items-center justify-between gap-4 px-6 py-8 md:px-16 lg:px-20">
        <Link
          href="/"
          className="group/brand inline-flex items-center gap-3.5 rounded outline-none focus-visible:ring-2 focus-visible:ring-white/70"
        >
          <Image
            src="/eventora-mark-v2.png"
            alt=""
            width={40}
            height={40}
            priority
            className="h-10 w-10 shrink-0 transition-transform duration-300 group-hover/brand:scale-105"
          />
          <span className="text-xl font-bold tracking-tight text-white sm:text-2xl">
            Eventora
          </span>
        </Link>

        <nav className="flex items-center gap-6 md:gap-10">
          <Link
            href="/search"
            className="hidden min-h-11 items-center rounded px-1 text-base font-medium text-white/90 outline-none transition-colors duration-200 hover:text-white focus-visible:ring-2 focus-visible:ring-white/70 sm:inline-flex"
          >
            Browse
          </Link>
          <Link
            href="/#organizers"
            className="hidden min-h-11 items-center rounded px-1 text-base font-medium text-white/90 outline-none transition-colors duration-200 hover:text-white focus-visible:ring-2 focus-visible:ring-white/70 sm:inline-flex"
          >
            For organizers
          </Link>
          <Link
            href="/auth/login"
            className="inline-flex min-h-11 items-center whitespace-nowrap rounded-full bg-white px-6 text-base font-semibold text-slate-900 shadow-lg shadow-white/10 outline-none transition-all duration-200 hover:bg-slate-100 focus-visible:ring-2 focus-visible:ring-white/70 active:scale-95"
          >
            Sign in
          </Link>
        </nav>
      </header>

      {/* Centre stack. A div, not <main>: the root layout already wraps every
          page in <main>, and nesting a second one is invalid. */}
      <div className="ws-stack relative z-10 flex flex-1 flex-col items-center justify-center px-6 py-12 text-center">
        <h1 className="ws-title mb-6 select-text text-6xl font-black tracking-tight text-white drop-shadow-sm sm:text-7xl md:text-8xl lg:text-[6.5rem]">
          Welcome.
        </h1>

        <p className="ws-tagline mb-10 max-w-2xl select-text text-lg leading-relaxed font-normal text-slate-100 sm:text-xl md:text-[1.28rem]">
          Live events across Egypt — find one, hold your seat for five minutes,
          and walk in with a QR code.
        </p>

        <Link
          href="/"
          className="group/cta inline-flex min-h-12 items-center gap-2.5 rounded-full bg-white px-8 py-3.5 text-base font-bold text-slate-950 shadow-xl shadow-black/25 outline-none transition-all duration-200 hover:scale-105 hover:bg-slate-100 focus-visible:ring-2 focus-visible:ring-white/70 active:scale-[0.98] sm:text-lg"
        >
          <span>Explore events</span>
          {/* The reference put group-hover on this arrow but never declared a
              group on the anchor, so it never moved. */}
          <ArrowRight
            className="h-5 w-5 transition-transform duration-200 group-hover/cta:translate-x-1"
            strokeWidth={2.2}
          />
        </Link>
      </div>

      {/* Keeps the headline optically centred against the taller header. */}
      <div className="ws-spacer h-10" aria-hidden="true" />
    </div>
  );
}
