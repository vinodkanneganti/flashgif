# ADR-00: Purpose and conventions of this ledger

**Status:** Accepted
**Date:** 2026-05-20

## Context
The project has accumulated dozens of meaningful architectural decisions, slice plans, and bug-fix learnings. Re-reading `progress.md` chronologically every time we revisit a topic doesn't scale.

## Decision
Maintain a flat, numbered ledger of ADRs under `architecture/`. Each file is one decision or one durable learning. Numbering starts at 00 and never gets reused; superseded ADRs stay in place with a `Status: Superseded by ADR-XX` header.

## Template
```
# ADR-XX: Title

**Status:** Accepted | Deferred | Superseded by ADR-YY
**Date:** YYYY-MM-DD
**Tags:** backend / web / ops / security / data / observability

## Context
What was true when we made the decision.

## Decision
What we chose.

## Rationale
Why this over the alternatives we considered.

## Consequences
What this commits us to. (Positive + negative.)

## Trigger to revisit (Deferred only)
The concrete event that should make us revisit.
```

## Consequences
- Index lives in `README.md`; ADRs are sorted by number, grouped by topic in the index.
- ADRs are intentionally short (half-page max). Detail lives in `progress.md` and `systemdesign.md`.
- Bug fixes that yield durable lessons get ADRs too — they're the most useful kind of context for the next person.
