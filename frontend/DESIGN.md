---
name: Kinetic Premier
colors:
  surface: '#f7f9fb'
  surface-dim: '#d8dadc'
  surface-bright: '#f7f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e6e8ea'
  surface-container-highest: '#e0e3e5'
  on-surface: '#191c1e'
  on-surface-variant: '#4a4455'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f3'
  outline: '#7b7487'
  outline-variant: '#ccc3d8'
  surface-tint: '#732ee4'
  primary: '#630ed4'
  on-primary: '#ffffff'
  primary-container: '#7c3aed'
  on-primary-container: '#ede0ff'
  inverse-primary: '#d2bbff'
  secondary: '#4b41e1'
  on-secondary: '#ffffff'
  secondary-container: '#645efb'
  on-secondary-container: '#fffbff'
  tertiary: '#474e64'
  on-tertiary: '#ffffff'
  tertiary-container: '#5e667d'
  on-tertiary-container: '#dee5ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#eaddff'
  primary-fixed-dim: '#d2bbff'
  on-primary-fixed: '#25005a'
  on-primary-fixed-variant: '#5a00c6'
  secondary-fixed: '#e2dfff'
  secondary-fixed-dim: '#c3c0ff'
  on-secondary-fixed: '#0f0069'
  on-secondary-fixed-variant: '#3323cc'
  tertiary-fixed: '#dae2fd'
  tertiary-fixed-dim: '#bec6e0'
  on-tertiary-fixed: '#131b2e'
  on-tertiary-fixed-variant: '#3f465c'
  background: '#f7f9fb'
  on-background: '#191c1e'
  surface-variant: '#e0e3e5'
typography:
  hero-headline:
    fontFamily: Inter
    fontSize: 56px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  hero-headline-mobile:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '700'
    lineHeight: '1.2'
    letterSpacing: -0.02em
  section-heading:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.05em
  caption:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
rounded:
  DEFAULT: 0.25rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  container-max: 1280px
  edge-padding: 24px
  gutter: 24px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 32px
  section-gap: 80px
---

## Brand & Style

The design system is engineered for a premium, high-fidelity event ticketing experience. The brand personality is sophisticated yet energetic, balancing the reliability of a high-end financial platform with the excitement of live entertainment. 

We utilize a **Modern Corporate** style with **Glassmorphism** influences for hero sections to create depth and a sense of "prestige." The aesthetic relies on high-contrast transitions between deep slate foundations and crisp, airy content areas. Visual interest is driven by precision geometry, vibrant violet accents, and expansive whitespace that allows high-quality event photography to lead the user experience.

## Colors

The palette is anchored by "Vibrant Violet," a color that signals creativity and exclusivity. 

- **Primary Action:** Use the linear gradient (Violet to Indigo) for primary conversion points and high-level brand moments.
- **Deep Slate:** Reserved for the Hero Background and Footer to provide a "dark mode" high-contrast frame for the light-themed content.
- **System States:** Use the `primary-hover` for interactive transitions. Background cards must remain pure `#FFFFFF` to ensure layered shadows remain visible and effective.
- **Borders:** Subtle `#E2E8F0` usage defines structure without introducing visual noise.

## Typography

This design system utilizes **Inter** exclusively to maintain a clean, systematic, and highly legible interface. 

- **Hero Headlines:** Employ tight line-heights and negative letter-spacing to create a high-impact, editorial feel. On mobile, these scale down to 36px to ensure readability.
- **Section Headings:** These provide clear logical breaks. Use `text-primary` for light backgrounds and `text-inverse` for dark sections.
- **Labels:** Small labels for "Date," "Category," or "Status" should use the uppercase `label-sm` style with increased tracking for a premium, utilitarian look.

## Layout & Spacing

The layout follows a **Fixed-Fluid Hybrid** model. Content is contained within a 1280px max-width wrapper, centered on the screen, with 24px of horizontal padding to ensure safe areas on smaller viewports.

- **Grid:** A 12-column system is used for desktop layouts.
- **Vertical Rhythm:** Sections are separated by an 80px gap to provide significant breathing room.
- **Mobile:** Margins remain at 24px, but 12-column grids collapse into a single-column stack, with card components usually spanning the full width of the container.

## Elevation & Depth

Hierarchy is established through **Layered Shadows** rather than heavy borders.

- **Base Layer:** Background Page (`#F8FAFC`).
- **Surface Layer:** White Cards (`#FFFFFF`) using `shadow-md` (0 4px 6px -1px rgb(0 0 0 / 0.1)).
- **Interactive Layer:** Hovered cards or dropdowns use `shadow-xl` (0 20px 25px -5px rgb(0 0 0 / 0.1)) to simulate "lift" toward the user.
- **Glass Effect:** Inside the Hero section, use a 10% white overlay with a 12px backdrop blur for search bars or secondary widgets to create a sophisticated, translucent depth.

## Shapes

The shape language is a mix of geometric precision and organic softness.

- **Containers & Cards:** Use a consistent `XL` (12px) radius. This applies to event cards, modal containers, and image thumbnails.
- **Interactive Elements:** Buttons, tags, and input fields utilize a **Full Pill** (9999px) radius. This creates a distinct visual contrast between the structural "containers" and the functional "actions."
- **Icons:** Use Lucide-react with a 2px stroke weight to match the clean, geometric lines of the typography.

## Components

### Buttons
- **Primary:** Full pill-shaped, using the violet-to-indigo gradient. Text is white, semi-bold.
- **Secondary:** Full pill-shaped, transparent background with a 1px border of `border-primary`.
- **Tertiary:** Text-only with a trailing icon (e.g., "View All →").

### Event Cards
- **Structure:** 12px rounded corners, `shadow-md`.
- **Image:** 16:9 aspect ratio at the top, no border-radius on the bottom two corners to sit flush against the content area.
- **Content:** Headline in `text-primary`, price/date in `text-primary`.

### Input Fields & Search
- **Search Bar:** Large, pill-shaped, with a subtle 1px border and a glassmorphism effect when used over hero sections. Use a `Search` icon at the start.

### Chips & Tags
- Used for "Category" (e.g., Music, Tech, Sports). Full-pill, light violet background (`#F5F3FF`) with violet text.

### Selection Controls
- **Checkboxes/Radios:** Use `bg-primary` for the active state with a white inner check/dot. 2px border width.