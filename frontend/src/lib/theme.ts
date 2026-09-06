/**
 * Theme plumbing shared by the pre-paint script and the toggle.
 *
 * There is no provider and no context. The theme lives in one place — the
 * `data-theme` attribute on `<html>` — because that is what the CSS reads, and
 * a second copy in React state would only be something to keep in sync.
 */

export type Theme = "light" | "dark";

export const THEME_STORAGE_KEY = "eventora-theme";

/**
 * Runs blocking, in `<head>`, before the first paint.
 *
 * It has to be inline and synchronous. Anything deferred — a module script, an
 * effect, `next/script` at any strategy other than the raw tag — runs after the
 * browser has already painted, which is the flash of the wrong theme that every
 * dark mode is judged on. It is a few hundred bytes and it is worth it.
 *
 * No stored value means "follow the system", which is why the fallback reads
 * prefers-color-scheme rather than defaulting to light. A `try` wraps the lot:
 * localStorage throws outright in a locked-down browser, and a theme script is
 * not a good reason for a blank page.
 */
export const THEME_INIT_SCRIPT = `(function(){try{var k=${JSON.stringify(
  THEME_STORAGE_KEY,
)},v=localStorage.getItem(k);if(v!=="light"&&v!=="dark"){v=window.matchMedia&&window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light";}document.documentElement.setAttribute("data-theme",v);}catch(e){document.documentElement.setAttribute("data-theme","light");}})();`;
