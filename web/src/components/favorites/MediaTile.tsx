"use client";

/**
 * Minimal "we know the id but not the full media row" tile.
 * Until the backend exposes GET /api/v1/media/{id}, favorites + collection
 * items are displayed as ID-only placeholders that link to /search by short id.
 */
export function MediaTilePlaceholder({ mediaId, subtitle }: { mediaId: string; subtitle?: string }) {
  return (
    <div className="rounded-lg border bg-muted p-4 aspect-[4/3] flex flex-col justify-between">
      <div className="text-xs text-muted-foreground break-all">{mediaId}</div>
      {subtitle && <div className="text-xs text-muted-foreground">{subtitle}</div>}
    </div>
  );
}
