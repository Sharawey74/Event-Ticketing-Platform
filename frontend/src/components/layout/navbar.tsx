"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter, usePathname } from "next/navigation";
import { FormEvent, useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  Search,
  ShoppingCart,
  LogOut,
  User,
  ChevronDown,
  LayoutDashboard,
  CalendarDays,
  X,
} from "lucide-react";

import { buildSearchHref } from "@/lib/search";
import { useAuthStore } from "@/store/authStore";
import { useReservationStore } from "@/store/reservationStore";
import { api } from "@/lib/api";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { SearchDropdown } from "./SearchDropdown";
import { CartDrawer } from "./CartDrawer";
import type { EventResponse, VenueResponse } from "@/types/event";

export function Navbar() {
  const router = useRouter();
  const pathname = usePathname() || "";

  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [isClient, setIsClient] = useState(false);
  const [inputFocused, setInputFocused] = useState(false);
  const [cartOpen, setCartOpen] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);

  const searchContainerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const cartCount = useReservationStore((s) => (s.bookingId ? 1 : 0));
  const { token, userEmail, userRole, clearAuth } = useAuthStore();

  useEffect(() => {
    const timer = setTimeout(() => setIsClient(true), 0);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  // Close profile dropdown on outside click
  useEffect(() => {
    if (!dropdownOpen) return;
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [dropdownOpen]);

  const { data: venuesData } = useQuery({
    queryKey: ["venues-for-search"],
    queryFn: async () => {
      const res = await api.get("/api/venues?size=100");
      return (res.data?.data?.content ?? []) as VenueResponse[];
    },
    staleTime: 5 * 60 * 1000,
    enabled: isClient,
  });

  const { data: eventSuggestions } = useQuery({
    queryKey: ["event-suggestions", debouncedQuery],
    queryFn: async () => {
      const res = await api.get(
        `/api/search/events?query=${encodeURIComponent(debouncedQuery)}&size=4`
      );
      return (res.data?.data?.content ?? []) as EventResponse[];
    },
    enabled: debouncedQuery.length >= 2,
    staleTime: 30_000,
  });

  const filteredCities = useMemo(() => {
    if (!venuesData || !query) return [];
    const allCities = venuesData.map((v) => v.city);
    const unique = [...new Set(allCities)].sort();
    return unique
      .filter((city) => city.toLowerCase().includes(query.toLowerCase()))
      .slice(0, 5);
  }, [venuesData, query]);

  const showDropdown =
    inputFocused &&
    query.length >= 1 &&
    (filteredCities.length > 0 || (eventSuggestions?.length ?? 0) > 0);

  function submitSearch(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    const searchTerm = query.trim();
    setInputFocused(false);
    if (!searchTerm) {
      router.push("/search");
      return;
    }
    router.push(buildSearchHref({ query: searchTerm, city: "", date: "", categoryId: "" }));
  }

  const handleSelectCity = useCallback(
    (city: string) => {
      setQuery(city);
      router.push(buildSearchHref({ query: "", city, date: "", categoryId: "" }));
    },
    [router]
  );

  const handleCloseDropdown = useCallback(() => setInputFocused(false), []);

  const handleLogout = () => {
    clearAuth();
    document.cookie = "token=; path=/; max-age=0;";
    setDropdownOpen(false);
    router.push("/auth/login");
  };

  // /welcome carries its own minimal bar, so the shared one would double up.
  if (
    pathname.includes("/confirmation") ||
    pathname.includes("/checkout") ||
    pathname.startsWith("/welcome")
  ) {
    return null;
  }

  const isAuthPage = pathname.startsWith("/auth");

  // Three shell states, per the redesign: signed out, attendee, organizer.
  // Gated on isClient because token and role rehydrate from localStorage after
  // mount — rendering the signed-in set on the server would mismatch.
  const isSignedIn = isClient && Boolean(token);
  const isOrganizer = isSignedIn && userRole === "ORGANIZER";

  // Every destination is an existing route. "Categories" from the mockup is
  // deliberately absent: there is no /categories page, and category filtering
  // already lives on /search.
  const navLinks: { href: string; label: string; active: boolean }[] = [
    { href: "/search", label: "Browse", active: pathname.startsWith("/search") },
  ];
  if (isOrganizer) {
    navLinks.push(
      {
        href: "/organizer/events",
        label: "My events",
        // Not startsWith, or "New event" would light up here too.
        active: pathname === "/organizer/events",
      },
      {
        href: "/organizer/events/new",
        label: "New event",
        active: pathname === "/organizer/events/new",
      }
    );
  } else if (isSignedIn) {
    navLinks.push({
      href: "/dashboard/bookings",
      label: "My bookings",
      active: pathname.startsWith("/dashboard"),
    });
  } else {
    navLinks.push({
      href: "/#organizers",
      label: "For organizers",
      active: false,
    });
  }

  const navLinkBase =
    "link-underline font-label-sm outline-none focus-visible:ring-2 focus-visible:ring-primary/50 rounded px-1 transition-colors duration-200";

  return (
    <>
      <header className="sticky top-0 z-30 h-20 bg-surface/80 backdrop-blur-md shadow-md border-b border-outline-variant flex items-center">
        <div className="mx-auto flex w-full max-w-container-max items-center justify-between gap-4 px-edge-padding">
          <Link href="/" className="group/brand inline-flex min-h-11 items-center gap-2 outline-none focus-visible:ring-2 focus-visible:ring-primary/50 rounded transition-transform duration-300 hover:scale-[1.03]">
            <Image
              src="/eventora-mark-v2.png"
              alt=""
              width={32}
              height={32}
              priority
              className="h-8 w-8 shrink-0"
            />
            {/* Steps down on mobile. At 375 the padded row is 327px wide, and
                the full-size wordmark plus both auth actions overflowed it. */}
            <span className="text-lg font-bold tracking-tighter text-primary transition-all duration-300 group-hover/brand:bg-linear-to-r group-hover/brand:from-primary group-hover/brand:to-secondary group-hover/brand:bg-clip-text group-hover/brand:text-transparent sm:text-section-heading">
              Eventora
            </span>
          </Link>

          <div ref={searchContainerRef} className="hidden md:flex relative w-full max-w-md">
            <form
              className="w-full flex items-center gap-2 rounded-full border border-outline-variant bg-surface-bright px-3 py-2 transition-all duration-300 focus-within:border-primary focus-within:bg-surface-container-lowest focus-within:shadow-md focus-within:shadow-primary/10 focus-within:ring-2 focus-within:ring-primary/40"
              onSubmit={submitSearch}
            >
              <Search className="h-4 w-4 text-outline shrink-0" />
              <input
                className="w-full bg-transparent font-body text-sm text-on-surface outline-none"
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onFocus={() => setInputFocused(true)}
                placeholder="Search events or cities"
                aria-label="Search events"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => { setQuery(""); setInputFocused(false); }}
                  className="text-on-surface-variant hover:text-on-surface"
                  aria-label="Clear search"
                >
                  <X className="h-4 w-4" />
                </button>
              )}
            </form>

            {showDropdown && (
              <SearchDropdown
                query={query}
                cities={filteredCities}
                events={eventSuggestions ?? []}
                onClose={handleCloseDropdown}
                onSelectCity={handleSelectCity}
              />
            )}
          </div>

          <div className="flex items-center gap-2 sm:gap-6">
            {/* Text links collapse below md, where they pushed the cart and
                account controls off-screen. Both destinations stay reachable:
                the logo goes to Discover, and the account menu carries
                Dashboard and My Events. */}
            <div className="hidden items-center gap-6 md:flex">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  className={`${navLinkBase} ${
                    link.active
                      ? "font-bold text-primary"
                      : "text-on-surface-variant hover:text-primary"
                  }`}
                  href={link.href}
                  aria-current={link.active ? "page" : undefined}
                >
                  {link.label}
                </Link>
              ))}
            </div>

            <ThemeToggle />

            {/* Cart button — hidden on auth pages */}
            {!isAuthPage && (
              <button
                type="button"
                onClick={() => setCartOpen(true)}
                className="group/cart relative inline-flex min-h-11 min-w-11 items-center justify-center rounded text-on-surface-variant outline-none transition-colors hover:text-primary focus-visible:ring-2 focus-visible:ring-primary/50"
                aria-label="Open cart"
              >
                <ShoppingCart className="h-6 w-6 transition-transform duration-300 group-hover/cart:scale-110 group-hover/cart:-rotate-6" />
                {cartCount > 0 && (
                  <span className="animate-scale-in absolute -right-2 -top-2 inline-flex h-5 w-5 items-center justify-center rounded-full bg-error text-on-error font-caption font-bold text-[10px] shadow-md shadow-error/40">
                    {cartCount}
                  </span>
                )}
              </button>
            )}

            {!isAuthPage && isClient && token ? (
              <div ref={dropdownRef} className="relative border-l border-outline-variant pl-4">
                <button
                  type="button"
                  onClick={() => setDropdownOpen((o) => !o)}
                  className="group/user flex items-center gap-1.5 outline-none focus-visible:ring-2 focus-visible:ring-primary/50 rounded"
                  aria-haspopup="true"
                  aria-expanded={dropdownOpen}
                >
                  <span className="w-8 h-8 rounded-full bg-primary/10 text-primary flex items-center justify-center font-bold text-sm shrink-0 transition-all duration-300 group-hover/user:bg-primary group-hover/user:text-on-primary group-hover/user:scale-105">
                    {userEmail ? userEmail.charAt(0).toUpperCase() : <User className="h-4 w-4" />}
                  </span>
                  <ChevronDown
                    className={`h-3.5 w-3.5 text-on-surface-variant transition-transform duration-150 ${dropdownOpen ? "rotate-180" : ""}`}
                  />
                </button>

                {dropdownOpen && (
                  <div className="animate-scale-in absolute right-0 top-full mt-2 w-56 rounded-xl border border-outline-variant bg-surface shadow-xl z-50 py-1 overflow-hidden origin-top-right">
                    <div className="px-4 py-3 border-b border-outline-variant">
                      <p className="text-xs text-on-surface-variant truncate">{userEmail}</p>
                      <span className="mt-1 inline-block rounded-full bg-primary/10 text-primary text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5">
                        {userRole === "ORGANIZER" ? "Organizer" : "Attendee"}
                      </span>
                    </div>

                    <Link
                      href="/dashboard/bookings"
                      onClick={() => setDropdownOpen(false)}
                      className="flex items-center gap-2 px-4 py-2.5 text-sm text-on-surface hover:bg-surface-container-low transition-colors"
                    >
                      <LayoutDashboard className="h-4 w-4 text-outline" />
                      My Bookings
                    </Link>

                    {userRole === "ORGANIZER" && (
                      <Link
                        href="/organizer/events"
                        onClick={() => setDropdownOpen(false)}
                        className="flex items-center gap-2 px-4 py-2.5 text-sm text-on-surface hover:bg-surface-container-low transition-colors"
                      >
                        <CalendarDays className="h-4 w-4 text-outline" />
                        Organizer Panel
                      </Link>
                    )}

                    <div className="h-px bg-outline-variant mx-4 my-1" />

                    <button
                      type="button"
                      onClick={handleLogout}
                      className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-error hover:bg-error/5 transition-colors"
                    >
                      <LogOut className="h-4 w-4" />
                      Sign out
                    </button>
                  </div>
                )}
              </div>
            ) : !isAuthPage ? (
              <div className="flex items-center gap-1 sm:gap-3">
                {/* Both shown at every width: measured at 375 the whole row is
                    312px, so there is room, and a returning user needs a login
                    affordance in the shell on pages that have no hero.
                    whitespace-nowrap because the tight mobile row otherwise
                    breaks these two-word labels across lines. */}
                <Link
                  className={`${navLinkBase} inline-flex min-h-11 items-center whitespace-nowrap text-on-surface-variant hover:text-primary`}
                  href="/auth/login"
                >
                  Log in
                </Link>
                <Link
                  className="btn-glass inline-flex min-h-11 items-center whitespace-nowrap px-4 py-2 font-label-sm text-[var(--fixed-cta-fg)] outline-none focus-visible:ring-2 focus-visible:ring-primary/50 sm:px-5"
                  href="/auth/register"
                >
                  Sign up
                </Link>
              </div>
            ) : null}
          </div>
        </div>
      </header>

      <CartDrawer isOpen={cartOpen} onClose={() => setCartOpen(false)} />
    </>
  );
}
