export function Footer() {
  return (
    <footer className="bg-inverse-surface py-stack-lg">
      <div className="mx-auto flex w-full max-w-container-max flex-col gap-4 px-edge-padding">
        <p className="text-section-heading text-surface font-bold tracking-tighter">VividPass</p>
        <p className="text-surface-variant font-caption max-w-md">
          Discover events, reserve your seats, and manage bookings from one place.
        </p>
        <div className="flex gap-4">
           <a href="#" className="text-surface-variant font-caption hover:text-primary-fixed-dim">Terms</a>
           <a href="#" className="text-surface-variant font-caption hover:text-primary-fixed-dim">Privacy</a>
        </div>
        <p className="text-surface-variant font-caption mt-4">
          © {new Date().getFullYear()} VividPass. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
