"use client";

import Link from "next/link";
import { useRouter, usePathname } from "next/navigation";
import { FormEvent, useState } from "react";

import { Search, ShoppingCart } from "lucide-react";

import { buildSearchHref } from "@/lib/search";

export function Navbar() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const cartCount = 0;

  function submitSearch(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();

    const searchTerm = query.trim();
    if (!searchTerm) {
      router.push("/search");
      return;
    }

    router.push(
      buildSearchHref({
        query: searchTerm,
        city: "",
        date: "",
        categoryId: "",
      }),
    );
  }

  const pathname = usePathname() || "";
  if (pathname.includes("/confirmation") || pathname.includes("/checkout")) {
    return null;
  }

  return (
    <header className="sticky top-0 z-30 h-20 bg-surface/80 backdrop-blur-md shadow-md border-b border-outline-variant flex items-center">
      <div className="mx-auto flex w-full max-w-container-max items-center justify-between gap-4 px-edge-padding">
        <Link href="/" className="inline-flex items-center gap-2">
          <span className="text-section-heading font-bold text-primary tracking-tighter">
            VividPass
          </span>
        </Link>

        <form
          className="hidden w-full max-w-md items-center gap-2 rounded-full border border-outline-variant bg-surface-bright px-3 py-2 md:flex"
          onSubmit={submitSearch}
        >
          <Search className="h-4 w-4 text-outline" />
          <input
            className="w-full bg-transparent font-body text-sm text-on-surface outline-none"
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search events"
            aria-label="Search events"
          />
        </form>

        <div className="flex items-center gap-6">
          <Link
            className={`font-label-sm ${pathname === "/" ? "font-bold text-primary border-b-2 border-primary" : "text-on-surface-variant hover:text-primary"}`}
            href="/"
          >
            Discover
          </Link>
          <Link
            className={`font-label-sm ${pathname.startsWith("/dashboard") ? "font-bold text-primary border-b-2 border-primary" : "text-on-surface-variant hover:text-primary"}`}
            href="/dashboard/bookings"
          >
            Dashboard
          </Link>
          <button
            className="relative inline-flex items-center justify-center text-on-surface-variant hover:text-primary transition-colors"
            type="button"
            aria-label="Open cart"
          >
            <ShoppingCart className="h-6 w-6" />
            <span className="absolute -right-2 -top-2 inline-flex h-5 w-5 items-center justify-center rounded-full bg-error text-on-error font-caption font-bold">
              {cartCount}
            </span>
          </button>
          
          <Link 
            className="bg-linear-to-r from-primary to-secondary text-on-primary rounded-full px-6 py-2 font-label-sm hover:shadow-lg hover:scale-105 transition-all" 
            href="/auth/login"
          >
            Sign in
          </Link>
        </div>
      </div>
    </header>
  );
}
