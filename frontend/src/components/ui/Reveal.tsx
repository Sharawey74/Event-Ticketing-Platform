"use client";

import { useEffect, useRef, type ElementType, type ReactNode } from "react";

interface RevealProps {
  children: ReactNode;
  /** Stagger within a group, in ms. */
  delay?: number;
  className?: string;
  /** Defaults to a div; pass "li", "section", etc. where the semantics matter. */
  as?: ElementType;
}

/**
 * Fades content up as it scrolls into view.
 *
 * Guarded twice, because content must never depend on an animation having run:
 * if IntersectionObserver is unavailable, everything is revealed immediately,
 * and `prefers-reduced-motion` is handled in globals.css, which forces
 * `.reveal` visible regardless of whether this component ever fires.
 */
export function Reveal({ children, delay = 0, className = "", as: Tag = "div" }: RevealProps) {
  const ref = useRef<HTMLElement | null>(null);

  // Toggles a class on the node rather than holding React state. Revealing is a
  // purely visual, one-way transition, so routing it through a re-render buys
  // nothing — and setting state synchronously in an effect (needed for the
  // no-IntersectionObserver path) triggers cascading renders.
  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    if (typeof IntersectionObserver === "undefined") {
      node.classList.add("is-revealed");
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("is-revealed");
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.08 },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return (
    <Tag
      ref={ref}
      className={`reveal ${className}`}
      style={{ ["--reveal-delay" as string]: `${delay}ms` }}
    >
      {children}
    </Tag>
  );
}
