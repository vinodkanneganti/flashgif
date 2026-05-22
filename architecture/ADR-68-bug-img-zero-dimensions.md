# ADR-68: `<img loading=lazy>` + `h-auto` rendered 0×0 in Playwright

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** bug, web, layout

## Context
**Symptom:** Playwright `await expect(tile).toBeVisible()` failed on the trending grid. DOM inspection confirmed the tiles existed (correct count, correct keys), but each had computed `width: Xpx; height: 0px`. Browser also showed the tiles as collapsed.

Root cause chain: the seeder uses `example.invalid` URLs, so the `<img>` never loads. With `loading="lazy"` the browser doesn't even attempt the request, and `<img>` with no successful load has no intrinsic dimensions. The Tailwind class was `h-auto`, which means "height comes from the content" — and the content (the image) had no dimensions, so the container collapsed to 0px. Playwright's `toBeVisible()` requires non-zero bounding box dimensions, so the visibility assertion failed even though the elements were "there".

## Decision
Reserve a fixed `aspect-ratio` on the tile container (via CSS class on the wrapper around `<img>`). The container's height is now derived from its width and the declared aspect ratio, independent of image load state. Skeleton/placeholder UX is the bonus — no layout shift when images finally arrive over slow networks.

## Rationale
- `aspect-ratio` is the modern, intrinsic-sizing answer to "I know the shape but not yet the content". Browser support is fine for our target matrix.
- Could have set explicit `width`/`height` attributes on `<img>` matching the rendition metadata. Works, but couples the layout to per-tile data and breaks for renditions of unknown size.
- Could have dropped `loading="lazy"` so the browser at least attempts the load and surfaces a broken-image icon with intrinsic dimensions. Rejected — lazy loading is correct for a long grid; we shouldn't undo a performance feature to fix a layout bug.

## Consequences
- All future tile/card components must reserve their aspect ratio on the wrapper, not depend on the image to size them.
- Playwright tests pass without fragile waits; `toBeVisible()` works on first paint.
- Long-term: when we add an image placeholder (blurhash or low-res preview), it slots into the reserved aspect-ratio container with zero layout work.
