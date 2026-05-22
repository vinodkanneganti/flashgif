# ADR-44: shadcn/ui (copy-paste) over UI library

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** web, ui

## Context
Need primitives (Button, Input, Dialog, Dropdown) that are themeable, accessible, and don't lock us into a specific design system. The alternatives are a full UI library (MUI, Mantine, Chakra) or rolling our own. Both extremes are bad — the first adds a multi-MB runtime dep we have to fight; the second reinvents accessibility patterns from scratch.

## Decision
Drop shadcn/ui-style primitives directly into `components/ui/` (Button, Input, Dialog, Dropdown, etc.). No runtime UI library dependency; shadcn is a copy-paste source, not an npm package.

## Rationale
- We own the source — theming is via Tailwind CSS variables; no upstream upgrades to chase or version mismatches.
- Zero bundle-size surprise — every line in `components/ui/` shows up in our own diff.
- Accessibility is solved by Radix primitives that shadcn wraps, so we get ARIA correctness without writing it.
- Customisation is free — we restyle by editing the component, not by overriding a third-party theme system.

## Consequences
- We're responsible for keeping primitives current — if Radix releases a fix, we copy it in (no automated upgrade).
- The `components/ui/` directory is treated as vendored code — minor formatting tweaks are fine, structural rewrites earn a comment explaining why.
- Reviewers check that page components don't reimplement what's already in `components/ui/` (Button, Input).
