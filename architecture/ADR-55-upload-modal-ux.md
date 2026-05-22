# ADR-55: Modal upload UX with post-publish redirect

**Status:** Accepted
**Date:** 2026-05-22 (Web Slice 3)
**Tags:** web, ux

## Context
Pinterest, Giphy, and Tenor all do upload as a contextual modal — you stay on the page you were browsing. A dedicated `/upload` page would force a context switch and an extra back-button. The upload flow itself has three logical stages (file pick → upload + transcode poll → metadata form) that all need to feel like one cohesive task.

## Decision
Header `+ Upload` button (visible only to authenticated users) opens a single 3-stage modal:
1. Dropzone (file pick + client-side validation).
2. Upload progress + transcode-status polling (READY / FAILED).
3. Metadata form (title, tags, content rating).

On successful publish, close the modal and redirect to `/channels/[me.username]` so the user sees their new upload land.

## Rationale
- Modal preserves browsing context — the user returns to the same scroll position if they cancel.
- One modal owns the whole pipeline — no cross-page state to thread, no half-finished uploads stranded on a separate page.
- Post-publish redirect to the channel page provides natural feedback ("your upload is live; here it is among your other work").

## Consequences
- Modal must be viewport-aware — capped at `max-h-[90vh]` with internal scroll so it works on small screens.
- The modal owns the polling lifecycle — closing it mid-poll cancels the React Query subscription but doesn't abort the upload itself.
- If we add bulk upload later, the modal grows or splits — today's design assumes one file per session.
