"use client";

import type { MediaSummary } from "@/lib/api/endpoints";
import { Heart } from "lucide-react";
import { useMe } from "@/lib/query/authHooks";
import { useFavoriteMutation, useMyFavoriteIds } from "@/lib/query/favoritesHooks";
import { cn } from "@/lib/utils";

export function MediaCard({ media }: { media: MediaSummary }) {
  const { data: me } = useMe();
  const { data: favIds } = useMyFavoriteIds();
  const favorited = !!favIds?.has(media.id);
  const favoriteMutation = useFavoriteMutation();

  const src =
    media.rendition_urls?.webp ??
    media.rendition_urls?.gif ??
    media.rendition_urls?.poster ??
    "";

  const w = media.width ?? 480;
  const h = media.height ?? 360;
  const aspectStyle = { aspectRatio: `${w} / ${h}` };

  return (
    <div
      className="group relative overflow-hidden rounded-lg bg-muted"
      style={aspectStyle}
    >
      {src && (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={src}
          alt={media.title}
          loading="lazy"
          width={w}
          height={h}
          className="w-full h-full object-cover block"
        />
      )}

      <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />

      <div className="absolute inset-x-0 bottom-0 p-3 text-white opacity-0 group-hover:opacity-100 transition-opacity">
        <div className="text-sm font-medium truncate">{media.title}</div>
        {media.tags?.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-1">
            {media.tags.slice(0, 3).map((t) => (
              <span key={t} className="text-xs px-1.5 py-0.5 rounded bg-white/20 backdrop-blur-sm">
                #{t}
              </span>
            ))}
          </div>
        )}
      </div>

      {me && (
        <button
          type="button"
          onClick={() => favoriteMutation.mutate({ mediaId: media.id, favorited })}
          className={cn(
            "absolute top-2 right-2 p-2 rounded-full text-white transition",
            favorited
              ? "bg-accent opacity-100"
              : "bg-black/50 opacity-0 group-hover:opacity-100 hover:bg-black/70",
          )}
          title={favorited ? "Remove from favorites" : "Add to favorites"}
          aria-label={favorited ? "Unfavorite" : "Favorite"}
          aria-pressed={favorited}
        >
          <Heart className={cn("h-4 w-4", favorited && "fill-current")} />
        </button>
      )}
    </div>
  );
}
