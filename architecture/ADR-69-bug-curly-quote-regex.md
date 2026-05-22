# ADR-69: Curly-quote heading vs ASCII-quote test regex

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 1)
**Tags:** bug, web, tests

## Context
**Symptom:** Playwright assertion `await expect(page.getByRole('heading', { name: /Results for "cat"/ })).toBeVisible()` failed. The heading was present and visible in the actual DOM.

Root cause: `SearchClient` renders the heading as `Results for &ldquo;{q}&rdquo;` — typographic (curly) quotes, `U+201C` / `U+201D`. The test regex used ASCII straight quotes, `U+0022`. The regex never matched, despite both rendering as "quotation marks" visually.

The heading itself is correct — typographic quotes are the right typography choice for user-visible copy. The test was the thing that needed to change.

## Decision
Loosen the test regex to match the user-meaningful part only: `/Results for .*cat/`. No quote characters in the assertion. The test now asserts the semantically important content (the search term echo) without binding to the exact glyphs used.

## Rationale
- Asserting against typography glyphs makes tests fragile against legitimate copy improvements (curly quotes today, em-dashes tomorrow, locale-aware quotes the day after).
- Could have copied the curly characters into the regex. Works, but creates a foot-cannon for anyone maintaining the test on a keyboard that doesn't make `U+201C` easy. Also breaks if the copy is later i18n'd.
- Could have switched the heading to ASCII quotes to match the regex. Wrong direction — the test should serve the product, not the other way around.

## Consequences
- House rule for Playwright text assertions: target the smallest semantically meaningful substring, not the full chrome. Avoids binding tests to copy or punctuation that may change for legitimate reasons.
- Documented in the targeted-coverage stance ([ADR-58](ADR-58-targeted-e2e.md)) — assertion brittleness was one reason we chose "few high-signal tests" over an exhaustive matrix.
- The curly-quote choice itself is reaffirmed: typography is part of polish, and tests must accommodate the polished output.
