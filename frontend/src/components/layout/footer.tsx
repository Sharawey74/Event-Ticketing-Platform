import Image from "next/image";
import Link from "next/link";

// Every href is an existing route. No /pricing, no /help-center, no category
// deep links — category ids are seed data, not a stable public contract.
const BROWSE_LINKS = [
  { label: "All events", href: "/search" },
  { label: "For organizers", href: "/#organizers" },
  { label: "Create an event", href: "/organizer/events/new" },
] as const;

const ACCOUNT_LINKS = [
  { label: "Log in", href: "/auth/login" },
  { label: "Register", href: "/auth/register" },
  { label: "My bookings", href: "/dashboard/bookings" },
] as const;

const LEGAL_LINKS = [
  { label: "Terms", href: "/terms" },
  { label: "Privacy", href: "/privacy" },
  { label: "Contact", href: "mailto:support@eventora.app" },
] as const;

const COLUMNS = [
  { heading: "Browse", links: BROWSE_LINKS },
  { heading: "Account", links: ACCOUNT_LINKS },
  { heading: "Legal", links: LEGAL_LINKS },
] as const;

export function Footer() {
  return (
    <footer className="relative overflow-hidden bg-surface-container-lowest border-t border-surface-container-high mt-auto">
      {/* Hairline of brand colour along the top edge. */}
      <div className="absolute inset-x-0 top-0 h-px bg-linear-to-r from-transparent via-primary/40 to-transparent" />

      <div className="mx-auto grid w-full max-w-container-max grid-cols-2 gap-8 px-edge-padding py-stack-lg sm:grid-cols-2 lg:grid-cols-4">
        <div className="col-span-2 flex flex-col gap-2 lg:col-span-1">
          <Link
            href="/"
            className="group/brand inline-flex min-h-11 w-fit items-center gap-2 rounded outline-none transition-transform duration-300 hover:scale-[1.03] focus-visible:ring-2 focus-visible:ring-primary/50"
          >
            {/* Explicit dimensions: an unsized next/image shifts layout on load.
                alt is empty because the wordmark beside it already names the
                brand — announcing it twice is noise for a screen reader. */}
            <Image
              src="/eventora-mark-v2.png"
              alt=""
              width={30}
              height={30}
              className="h-[30px] w-[30px] shrink-0"
            />
            <span className="text-section-heading font-bold tracking-tighter text-primary transition-all duration-300 group-hover/brand:bg-linear-to-r group-hover/brand:from-primary group-hover/brand:to-secondary group-hover/brand:bg-clip-text group-hover/brand:text-transparent">
              Eventora
            </span>
          </Link>
          <p className="max-w-xs font-caption text-on-surface-variant">
            Ticketing for live events across Egypt. Prices in EGP — the total you
            see is the total you pay.
          </p>
        </div>

        {COLUMNS.map((column) => (
          <div key={column.heading} className="flex flex-col gap-3">
            <span className="mb-1 font-label-sm uppercase tracking-wider text-outline-text">
              {column.heading}
            </span>
            {column.links.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                className="group/fl inline-flex w-fit items-center gap-1.5 rounded py-1 font-caption text-on-surface-variant outline-none transition-colors hover:text-primary focus-visible:ring-2 focus-visible:ring-primary/50"
              >
                <span className="h-px w-0 bg-primary transition-all duration-300 group-hover/fl:w-3" />
                {link.label}
              </Link>
            ))}
          </div>
        ))}
      </div>

      <div className="border-t border-surface-container-high">
        <div className="mx-auto flex w-full max-w-container-max flex-col items-center justify-between gap-2 px-edge-padding py-stack-md text-center sm:flex-row sm:text-left">
          <p className="font-caption text-on-surface-variant">
            © {new Date().getFullYear()} Eventora. All rights reserved.
          </p>
          <p className="font-caption text-outline-text">
            Payments by Stripe · EGP
          </p>
        </div>
      </div>
    </footer>
  );
}
