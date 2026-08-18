/*
 * Four small behaviours. No framework, no build step — every page is readable
 * and navigable with this file blocked.
 */
(function () {
  "use strict";

  /**
   * Mark the current page in the nav.
   *
   * Done here rather than by hand in three files so a renamed page cannot end up
   * highlighted on the wrong one. Falls back to index.html for a bare directory
   * URL, which is what GitHub Pages serves at the site root.
   */
  function markCurrentPage() {
    var here = location.pathname.split("/").pop() || "index.html";
    var links = document.querySelectorAll(".nav a, .nav-mobile a");

    for (var i = 0; i < links.length; i++) {
      if (links[i].getAttribute("href") === here) {
        links[i].setAttribute("aria-current", "page");
      } else {
        links[i].removeAttribute("aria-current");
      }
    }
  }

  /**
   * Mobile nav disclosure. The markup ships closed and the button carries
   * aria-expanded, so the state is announced rather than merely visible.
   */
  function mobileNav() {
    var toggle = document.querySelector(".nav-toggle");
    var menu = document.querySelector(".nav-mobile");
    if (!toggle || !menu) return;

    toggle.addEventListener("click", function () {
      var open = menu.classList.toggle("is-open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
  }

  /**
   * Fade sections in as they arrive.
   *
   * Guarded twice. Without IntersectionObserver, or under
   * prefers-reduced-motion, everything is revealed immediately instead of being
   * left invisible — content must never depend on an animation having run.
   */
  function revealOnScroll() {
    var items = document.querySelectorAll(".reveal");
    if (!items.length) return;

    var still =
      !("IntersectionObserver" in window) ||
      (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches);

    if (still) {
      for (var i = 0; i < items.length; i++) items[i].classList.add("is-in");
      return;
    }

    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          entry.target.classList.add("is-in");
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.06 }
    );

    for (var j = 0; j < items.length; j++) observer.observe(items[j]);
  }

  /**
   * Click a screenshot to see it uncropped.
   *
   * The overlay is built in JS, so with JS blocked there is no zoom-in cursor
   * promising something that cannot happen. Escape closes it, because a
   * click-anywhere-to-dismiss overlay with no keyboard route is a trap.
   */
  function lightbox() {
    var shots = document.querySelectorAll(".shot img");
    if (!shots.length) return;

    var box = document.createElement("div");
    box.className = "lightbox";
    box.setAttribute("role", "dialog");
    box.setAttribute("aria-modal", "true");
    box.setAttribute("aria-label", "Enlarged screenshot");

    var full = document.createElement("img");
    full.alt = "";
    box.appendChild(full);
    document.body.appendChild(box);

    function close() {
      box.classList.remove("is-open");
      full.removeAttribute("src");
    }

    for (var i = 0; i < shots.length; i++) {
      (function (img) {
        img.addEventListener("click", function () {
          full.src = img.currentSrc || img.src;
          full.alt = img.alt;
          box.classList.add("is-open");
        });
      })(shots[i]);
    }

    box.addEventListener("click", close);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && box.classList.contains("is-open")) close();
    });
  }

  function init() {
    markCurrentPage();
    mobileNav();
    revealOnScroll();
    lightbox();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
