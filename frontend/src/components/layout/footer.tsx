import Link from "next/link";

export function Footer() {
  return (
    <footer className="bg-surface-container-lowest border-t border-surface-container-high py-stack-lg mt-auto">
      <div className="mx-auto flex w-full max-w-container-max flex-col md:flex-row items-center justify-between gap-4 px-edge-padding text-center md:text-left">
        <div>
          <p className="text-section-heading text-primary font-bold tracking-tighter">Eventora</p>
          <p className="text-on-surface-variant font-caption mt-1">
            Discover events, reserve your seats, and manage bookings from one place.
          </p>
        </div>
        <div className="flex flex-col items-center md:items-end gap-2">
          <div className="flex gap-4">
             <Link href="/terms" className="text-on-surface-variant font-caption hover:text-primary transition-colors">Terms</Link>
             <Link href="/privacy" className="text-on-surface-variant font-caption hover:text-primary transition-colors">Privacy</Link>
          </div>
          <p className="text-on-surface-variant font-caption">
            © {new Date().getFullYear()} Eventora. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
