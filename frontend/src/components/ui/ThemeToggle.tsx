"use client";

import { useCallback, useEffect, useSyncExternalStore } from "react";
import { Moon, Sun } from "lucide-react";

import { THEME_STORAGE_KEY, type Theme } from "@/lib/theme";

/**
 * The theme is not React state. It lives in one place — the `data-theme`
 * attribute on `<html>` — because that is what the CSS reads, and because the
 * pre-paint script in the document head sets it before React exists.
 *
 * So this subscribes to that attribute rather than mirroring it. A copy in
 * useState would need an effect to seed it on mount, which is both a lint error
 * (`react-hooks/set-state-in-effect`) and a real cascading render — and would
 * leave two things entitled to decide what the theme is.
 */

const subscribe = (onStoreChange: () => void) => {
  const observer = new MutationObserver(onStoreChange);
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["data-theme"],
  });
  return () => observer.disconnect();
};

const getSnapshot = (): Theme =>
  document.documentElement.getAttribute("data-theme") === "dark"
    ? "dark"
    : "light";

// The server cannot know what the pre-paint script will choose. Returning a
// fixed value here is the documented contract: React hydrates against it, then
// immediately re-renders with the client snapshot if the two disagree — no
// mismatch warning, and no effect needed to catch up.
const getServerSnapshot = (): Theme => "light";

export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const isDark = theme === "dark";

  // Until the user picks a side the OS still owns the decision, so a system
  // switch mid-session is followed. Once a choice is stored it wins and this
  // stops touching anything. Writes the attribute only — the subscription above
  // is what turns that into a render.
  useEffect(() => {
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const onSystemChange = (event: MediaQueryListEvent) => {
      try {
        if (localStorage.getItem(THEME_STORAGE_KEY)) return;
      } catch {
        return;
      }
      document.documentElement.setAttribute(
        "data-theme",
        event.matches ? "dark" : "light",
      );
    };
    query.addEventListener("change", onSystemChange);
    return () => query.removeEventListener("change", onSystemChange);
  }, []);

  const toggle = useCallback(() => {
    // Read the attribute rather than the render's `theme`, so a click that
    // lands in the same tick as a system change still flips from what is
    // actually on screen.
    const next: Theme =
      document.documentElement.getAttribute("data-theme") === "dark"
        ? "light"
        : "dark";
    document.documentElement.setAttribute("data-theme", next);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // Private mode, or site data blocked. The theme still changes for this
      // page view, it just will not be remembered. Nothing to tell the user.
    }
  }, []);

  const label = isDark ? "Switch to light theme" : "Switch to dark theme";

  return (
    <button
      type="button"
      onClick={toggle}
      className="inline-flex min-h-11 min-w-11 items-center justify-center rounded text-on-surface-variant outline-none transition-colors hover:text-primary focus-visible:ring-2 focus-visible:ring-primary/50"
      aria-label={label}
      title={label}
    >
      {/* Both icons stay mounted and cross-fade rather than swapping, so the
          button keeps a fixed size and the row never reflows. Hidden from
          assistive tech — the accessible name is on the button. */}
      <span className="relative block h-5 w-5" aria-hidden="true">
        <Sun
          className={`absolute inset-0 h-5 w-5 transition-all duration-300 ${
            isDark
              ? "rotate-90 scale-50 opacity-0"
              : "rotate-0 scale-100 opacity-100"
          }`}
        />
        <Moon
          className={`absolute inset-0 h-5 w-5 transition-all duration-300 ${
            isDark
              ? "rotate-0 scale-100 opacity-100"
              : "-rotate-90 scale-50 opacity-0"
          }`}
        />
      </span>
    </button>
  );
}
