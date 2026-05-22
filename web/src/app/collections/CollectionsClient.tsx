"use client";

import Link from "next/link";
import { useState } from "react";
import { Lock, Globe, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useCreateCollectionMutation, useMyCollections } from "@/lib/query/favoritesHooks";

export function CollectionsClient() {
  const { data, isLoading } = useMyCollections();
  const [showNew, setShowNew] = useState(false);

  return (
    <div className="container mx-auto px-4 py-6 space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Your collections</h1>
        <Button onClick={() => setShowNew(true)} className="gap-1">
          <Plus className="h-4 w-4" /> New collection
        </Button>
      </div>

      {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}

      {data && data.length === 0 && (
        <p className="text-sm text-muted-foreground">
          No collections yet. Create one to start organising favorites.
        </p>
      )}

      {data && data.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {data.map((c) => (
            <Link
              key={c.id}
              href={`/collections/${c.id}`}
              className="rounded-lg border p-4 hover:border-primary transition-colors block"
            >
              <div className="flex items-start justify-between gap-2">
                <h3 className="font-medium truncate">{c.name}</h3>
                {c.is_public ? (
                  <Globe className="h-4 w-4 text-muted-foreground shrink-0" aria-label="Public" />
                ) : (
                  <Lock className="h-4 w-4 text-muted-foreground shrink-0" aria-label="Private" />
                )}
              </div>
              {c.description && (
                <p className="mt-1 text-sm text-muted-foreground line-clamp-2">{c.description}</p>
              )}
              <p className="mt-3 text-xs text-muted-foreground">
                Created {new Date(c.created_at).toLocaleDateString()}
              </p>
            </Link>
          ))}
        </div>
      )}

      {showNew && <NewCollectionDialog onClose={() => setShowNew(false)} />}
    </div>
  );
}

function NewCollectionDialog({ onClose }: { onClose: () => void }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [isPublic, setIsPublic] = useState(false);
  const create = useCreateCollectionMutation();

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="New collection"
      className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if (!name.trim()) return;
          await create.mutateAsync({ name: name.trim(), description: description.trim() || undefined, is_public: isPublic });
          onClose();
        }}
        className="w-full max-w-md rounded-lg bg-background border shadow-xl p-4 space-y-3"
      >
        <h2 className="text-lg font-semibold">New collection</h2>

        <div className="space-y-1">
          <label htmlFor="name" className="text-sm font-medium">Name</label>
          <Input id="name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
        </div>
        <div className="space-y-1">
          <label htmlFor="desc" className="text-sm font-medium">Description (optional)</label>
          <Input id="desc" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={4000} />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={isPublic}
            onChange={(e) => setIsPublic(e.target.checked)}
          />
          Make public
        </label>

        {create.isError && (
          <div role="alert" className="text-xs text-destructive">Failed to create.</div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={create.isPending || !name.trim()}>
            {create.isPending ? "Creating…" : "Create"}
          </Button>
        </div>
      </form>
    </div>
  );
}
