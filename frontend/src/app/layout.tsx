import type { Metadata } from "next";

import { SiteFooter } from "@/components/layout/SiteFooter";
import { Navbar } from "@/components/layout/navbar";
import { ReservationGuard } from "@/components/layout/ReservationGuard";
import { AppProviders } from "@/components/providers/app-providers";
import { THEME_INIT_SCRIPT } from "@/lib/theme";
import "./globals.css";

export const metadata: Metadata = {
  title: "Eventora — Find Events Worth Leaving the House For",
  description:
    "Discover, book, and manage event tickets in seconds. Eventora connects you to the best live events in your city.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased" suppressHydrationWarning>
      <head>
        {/* Stamps data-theme on <html> before the first paint. Next hoists its
            own stylesheet link above this in the emitted head, which does not
            matter: an inline script with no async/defer executes during head
            parsing, so it lands before the body exists and therefore before
            anything is painted. What would matter is deferring it — a
            component, an effect, or next/script at any strategy paints the
            default theme first and then flips, which is the flash this exists
            to avoid. <html> carries suppressHydrationWarning for exactly this
            attribute. */}
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap"
          rel="stylesheet"
        />
      </head>
      <body
        className="min-h-full bg-surface text-on-surface"
        suppressHydrationWarning
      >
        <AppProviders>
          <ReservationGuard />
          <div className="flex min-h-full flex-col">
            <Navbar />
            <main className="flex-1">{children}</main>
            <SiteFooter />
          </div>
        </AppProviders>
      </body>
    </html>
  );
}
