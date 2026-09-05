"use client";

import { usePathname } from "next/navigation";

import { Footer } from "./footer";

/**
 * Renders the shared footer everywhere except the routes that supply their own
 * full-height chrome.
 *
 * A thin client wrapper rather than a route group: moving all fourteen existing
 * routes into an `(app)` folder to give `/welcome` a sibling layout would be a
 * large, risky refactor for one page. `Navbar` already suppresses itself the
 * same way, so both halves of the shell now use one pattern.
 */
const CHROMELESS_PREFIXES = ["/welcome"];

export function SiteFooter() {
  const pathname = usePathname() || "";
  if (CHROMELESS_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
    return null;
  }
  return <Footer />;
}
