"use client";

import Masonry from "react-masonry-css";
import type { MediaSummary } from "@/lib/api/endpoints";
import { MediaCard } from "./MediaCard";

const BREAKPOINT_COLS = {
  default: 4,
  1280: 3,
  900: 2,
  500: 1,
};

export function MasonryGrid({ items }: { items: MediaSummary[] }) {
  if (items.length === 0) {
    return (
      <div className="text-center py-16 text-muted-foreground">
        No results.
      </div>
    );
  }

  return (
    <Masonry
      breakpointCols={BREAKPOINT_COLS}
      className="masonry-grid"
      columnClassName="masonry-column"
    >
      {items.map((m) => (
        <MediaCard key={m.id} media={m} />
      ))}
    </Masonry>
  );
}
