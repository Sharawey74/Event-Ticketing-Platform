"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";

import { Search, ShoppingCart, Ticket } from "lucide-react";

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

  return (
    <header className="sticky top-0 z-30 border-b border-black/10 bg-white/85 backdrop-blur-xl">
      <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4 px-4 py-3 md:px-6">
        <Link href="/" className="inline-flex items-center gap-2">
          <span className="rounded-lg bg-emerald-600 p-2 text-white">
            <Ticket className="h-4 w-4" />
          </span>
          <span className="font-semibold tracking-tight text-zinc-900">
            Event Ticketing
          </span>
        </Link>

        <form
          className="hidden w-full max-w-md items-center gap-2 rounded-full border border-zinc-200 bg-white px-3 py-2 md:flex"
          onSubmit={submitSearch}
        >
          <Search className="h-4 w-4 text-zinc-500" />
          <input
            className="w-full bg-transparent text-sm text-zinc-700 outline-none"
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search events"
            aria-label="Search events"
          />
        </form>

        <div className="flex items-center gap-4">
          <Link className="text-sm font-medium text-zinc-700 hover:text-zinc-950" href="/auth">
            Sign in
          </Link>
          <button
            className="relative inline-flex h-10 w-10 items-center justify-center rounded-full border border-zinc-300 text-zinc-800 transition hover:border-zinc-900"
            type="button"
            aria-label="Open cart"
          >
            <ShoppingCart className="h-4 w-4" />
            <span className="absolute -right-1 -top-1 inline-flex min-h-5 min-w-5 items-center justify-center rounded-full bg-emerald-600 px-1 text-xs font-semibold text-white">
              {cartCount}
            </span>
          </button>
        </div>
      </div>
    </header>
  );
}
