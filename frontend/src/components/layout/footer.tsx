import Link from "next/link";

const PRODUCT_LINKS = [
  { label: "Browse Events", href: "/search" },
  { label: "For Organizers", href: "/#organizers" },
  { label: "Create an Event", href: "/organizer/events/new" },
] as const;

const SUPPORT_LINKS = [
  { label: "Contact", href: "mailto:support@eventora.app" },
  { label: "Terms", href: "/terms" },
  { label: "Privacy", href: "/privacy" },
] as const;

export function Footer() {
  return (
    <footer className="relative overflow-hidden bg-surface-container-lowest border-t border-surface-container-high mt-auto">
      {/* Hairline of brand colour along the top edge. */}
      <div className="absolute inset-x-0 top-0 h-px bg-linear-to-r from-transparent via-primary/40 to-transparent" />

      <div className="mx-auto grid w-full max-w-container-max grid-cols-1 gap-8 px-edge-padding py-stack-lg sm:grid-cols-3">
        <div className="flex flex-col gap-2 sm:col-span-1">
          <Link
            href="/"
            className="text-section-heading text-primary font-bold tracking-tighter w-fit transition-all duration-300 hover:bg-linear-to-r hover:from-primary hover:to-secondary hover:bg-clip-text hover:text-transparent"
          >
            Eventora
          </Link>
          <p className="text-on-surface-variant font-caption max-w-xs">
            Discover events, reserve your seats, and manage bookings from one place.
          </p>
        </div>

        <div className="flex flex-col gap-2">
          <span className="font-label-sm uppercase tracking-wider text-on-surface-variant/70 mb-1">
            Product
          </span>
          {PRODUCT_LINKS.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              className="group/fl inline-flex items-center gap-1.5 text-on-surface-variant font-caption hover:text-primary transition-colors w-fit"
            >
              <span className="h-px w-0 bg-primary transition-all duration-300 group-hover/fl:w-3" />
              {link.label}
            </Link>
          ))}
        </div>

        <div className="flex flex-col gap-2">
          <span className="font-label-sm uppercase tracking-wider text-on-surface-variant/70 mb-1">
            Support
          </span>
          {SUPPORT_LINKS.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              className="group/fl inline-flex items-center gap-1.5 text-on-surface-variant font-caption hover:text-primary transition-colors w-fit"
            >
              <span className="h-px w-0 bg-primary transition-all duration-300 group-hover/fl:w-3" />
              {link.label}
            </Link>
          ))}
        </div>
      </div>

      <div className="border-t border-surface-container-high">
        <div className="mx-auto flex w-full max-w-container-max flex-col sm:flex-row items-center justify-between gap-2 px-edge-padding py-stack-md text-center sm:text-left">
          <p className="text-on-surface-variant font-caption">
            © {new Date().getFullYear()} Eventora. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
