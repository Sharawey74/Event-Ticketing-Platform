"use client";

import Image from "next/image";
import Link from "next/link";
import { ArrowRight } from "lucide-react";

import { AuroraBackdrop } from "@/components/home/AuroraBackdrop";

/**
 * A standalone entry page. Same aurora and visual language as the hero, a
 * minimal bar, and one button through to the home page.
 *
 * Nothing redirects here and nothing is stored to remember a visit — it is a
 * page you link to deliberately, not a gate in front of `/`.
 *
 * The reference bar read HOME / NOTIFICATION / ABOUT / HELP. Three of those
 * have no route and none should be invented, so the links here are real
 * destinations. Dead nav is worse than no nav.
 */
export default function WelcomePage() {
  return (
    <div className="min-h-[100svh] bg-[var(--night-000)] p-3 sm:p-6">
      <div className="relative flex min-h-[calc(100svh-1.5rem)] flex-col overflow-hidden rounded-[20px] sm:min-h-[calc(100svh-3rem)] sm:rounded-[28px]">
        <AuroraBackdrop />

        {/* Bar */}
        <header className="relative z-10 flex items-center justify-between gap-4 px-5 py-5 sm:px-9 sm:py-7">
          <Link
            href="/"
            className="group/brand inline-flex items-center gap-2.5 rounded outline-none focus-visible:ring-2 focus-visible:ring-white/70"
          >
            <Image
              src="/eventora-mark-v2.png"
              alt=""
              width={34}
              height={34}
              priority
              className="h-[34px] w-[34px] shrink-0"
            />
            <span className="text-lg font-bold tracking-tight text-white sm:text-xl">
              Eventora
            </span>
          </Link>

          <nav className="flex items-center gap-1 sm:gap-5">
            <Link
              href="/search"
              className="hidden min-h-11 items-center rounded px-2 text-sm font-medium tracking-wide text-white/75 outline-none transition-colors hover:text-white focus-visible:ring-2 focus-visible:ring-white/70 sm:inline-flex"
            >
              Browse
            </Link>
            <Link
              href="/#organizers"
              className="hidden min-h-11 items-center rounded px-2 text-sm font-medium tracking-wide text-white/75 outline-none transition-colors hover:text-white focus-visible:ring-2 focus-visible:ring-white/70 sm:inline-flex"
            >
              For organizers
            </Link>
            <Link
              href="/auth/login"
              className="inline-flex min-h-11 items-center whitespace-nowrap rounded-full bg-white px-5 text-sm font-semibold text-[var(--night-000)] outline-none transition-transform duration-200 hover:scale-[1.03] focus-visible:ring-2 focus-visible:ring-white/70"
            >
              Sign in
            </Link>
          </nav>
        </header>

        {/* Centre stack. A div, not <main>: the root layout already wraps
            every page in <main>, and nesting a second one is invalid. */}
        <div className="relative z-10 flex flex-1 flex-col items-center justify-center px-6 pb-16 text-center">
          <h1 className="word-rise text-5xl font-bold tracking-tight text-white sm:text-6xl md:text-7xl">
            {"Welcome.".split(" ").map((word, index) => (
              <span key={word} style={{ "--word-index": index } as React.CSSProperties}>
                {word}
              </span>
            ))}
          </h1>

          <p className="animate-fade-up mt-5 max-w-md text-balance text-base leading-relaxed text-white/70 sm:text-lg">
            Live events across Egypt — find one, hold your seat for five minutes,
            and walk in with a QR code.
          </p>

          <Link
            href="/"
            className="animate-fade-up group/cta mt-9 inline-flex min-h-12 items-center gap-2 rounded-full bg-white px-7 text-base font-semibold text-[var(--night-000)] outline-none transition-transform duration-200 hover:scale-[1.04] focus-visible:ring-2 focus-visible:ring-white/70"
            style={{ animationDelay: "220ms" }}
          >
            Explore events
            <ArrowRight className="h-4 w-4 transition-transform duration-300 group-hover/cta:translate-x-1" />
          </Link>
        </div>
      </div>
    </div>
  );
}
