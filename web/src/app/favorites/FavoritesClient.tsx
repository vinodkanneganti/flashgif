"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { MediaTilePlaceholder } from "@/components/favorites/MediaTile";
import { useMyFavorites } from "@/lib/query/favoritesHooks";

export function FavoritesClient() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useMyFavorites(page);

  return (
    <div className="container mx-auto px-4 py-6 space-y-6">
      <h1 className="text-2xl font-semibold">Your favorites</h1>

      {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}

      {data && data.items.length === 0 && (
        <p className="text-sm text-muted-foreground">
          No favorites yet. Find something you love on{" "}
          <a href="/" className="text-primary hover:underline">trending</a>.
        </p>
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {data.items.map((f) => (
              <MediaTilePlaceholder
                key={f.media_id}
                mediaId={f.media_id}
                subtitle={new Date(f.created_at).toLocaleString()}
              />
            ))}
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm text-muted-foreground">
              Page {data.page + 1} · {data.total} total
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => p + 1)}
                disabled={(page + 1) * data.size >= data.total}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
