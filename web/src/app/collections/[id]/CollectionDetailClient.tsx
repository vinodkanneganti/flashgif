"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Lock, Globe, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MediaTilePlaceholder } from "@/components/favorites/MediaTile";
import { useMe } from "@/lib/query/authHooks";
import {
  useCollection, useCollectionItems, useDeleteCollectionMutation, useUpdateCollectionMutation,
} from "@/lib/query/favoritesHooks";

export function CollectionDetailClient({ id }: { id: string }) {
  const router = useRouter();
  const { data: me } = useMe();
  const { data: collection, isLoading } = useCollection(id);
  const { data: items } = useCollectionItems(id);
  const updateMut = useUpdateCollectionMutation(id);
  const deleteMut = useDeleteCollectionMutation();

  if (isLoading) return <p className="container mx-auto px-4 py-6 text-sm text-muted-foreground">Loading…</p>;
  if (!collection) {
    return (
      <p className="container mx-auto px-4 py-6 text-sm text-muted-foreground">
        Collection not found.{" "}
        <Link href="/" className="text-primary hover:underline">Back home</Link>.
      </p>
    );
  }

  const isOwner = !!me && me.id === collection.owner_id;

  return (
    <div className="container mx-auto px-4 py-6 space-y-6">
      <header className="space-y-2">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">{collection.name}</h1>
          {collection.is_public ? (
            <Globe className="h-4 w-4 text-muted-foreground" aria-label="Public" />
          ) : (
            <Lock className="h-4 w-4 text-muted-foreground" aria-label="Private" />
          )}
        </div>
        {collection.description && (
          <p className="text-sm text-muted-foreground">{collection.description}</p>
        )}
        {isOwner && (
          <div className="flex gap-2 pt-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => updateMut.mutate({ is_public: !collection.is_public })}
              disabled={updateMut.isPending}
            >
              Make {collection.is_public ? "private" : "public"}
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={async () => {
                if (!confirm("Delete this collection?")) return;
                await deleteMut.mutateAsync(id);
                router.push("/collections");
              }}
              className="gap-1"
            >
              <Trash2 className="h-4 w-4" /> Delete
            </Button>
          </div>
        )}
      </header>

      {items && items.items.length === 0 && (
        <p className="text-sm text-muted-foreground">No items yet.</p>
      )}

      {items && items.items.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {items.items.map((i) => (
            <MediaTilePlaceholder
              key={i.media_id}
              mediaId={i.media_id}
              subtitle={`added ${new Date(i.added_at).toLocaleDateString()}`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
