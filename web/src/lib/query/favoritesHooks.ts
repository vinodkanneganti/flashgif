"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  addFavorite, addToCollection, createCollection, deleteCollection,
  getCollection, listCollectionItems, listMyCollections, listMyFavorites,
  removeFavorite, removeFromCollection, updateCollection,
  type Collection, type Paged, type Favorite,
} from "@/lib/api/favorites";
import { queryKeys } from "./keys";

// ----- favorites -----

export function useMyFavorites(page = 0) {
  return useQuery({
    queryKey: queryKeys.myFavorites(page),
    queryFn: () => listMyFavorites(page),
  });
}

/**
 * Set of media_ids the current user has favorited. Powers per-card heart state.
 * Cached on first use; mutations invalidate.
 */
export function useMyFavoriteIds() {
  return useQuery({
    queryKey: queryKeys.myFavoriteIds(),
    queryFn: async () => {
      const all = new Set<string>();
      let page = 0;
      // Bounded loop; backend pagination cap = 100 per page, total cap = ~5000.
      for (let i = 0; i < 50; i++) {
        const r: Paged<Favorite> = await listMyFavorites(page, 100);
        r.items.forEach((f) => all.add(f.media_id));
        if ((page + 1) * r.size >= r.total) break;
        page++;
      }
      return all;
    },
    staleTime: 60_000,
  });
}

export function useFavoriteMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ mediaId, favorited }: { mediaId: string; favorited: boolean }) =>
      favorited ? removeFavorite(mediaId) : addFavorite(mediaId),
    onMutate: async ({ mediaId, favorited }) => {
      await qc.cancelQueries({ queryKey: queryKeys.myFavoriteIds() });
      const previous = qc.getQueryData<Set<string>>(queryKeys.myFavoriteIds());
      const next = new Set(previous ?? []);
      if (favorited) next.delete(mediaId); else next.add(mediaId);
      qc.setQueryData(queryKeys.myFavoriteIds(), next);
      return { previous };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.previous) qc.setQueryData(queryKeys.myFavoriteIds(), ctx.previous);
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: queryKeys.myFavoriteIds() });
      qc.invalidateQueries({ queryKey: ["favorites", "me"] });
    },
  });
}

// ----- collections -----

export function useMyCollections() {
  return useQuery({
    queryKey: queryKeys.myCollections(),
    queryFn: listMyCollections,
  });
}

export function useCollection(id: string) {
  return useQuery({
    queryKey: queryKeys.collection(id),
    queryFn: () => getCollection(id),
    enabled: !!id,
  });
}

export function useCollectionItems(id: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.collectionItems(id, page),
    queryFn: () => listCollectionItems(id, page),
    enabled: !!id,
  });
}

export function useCreateCollectionMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; description?: string; is_public?: boolean }) =>
      createCollection(input),
    onSuccess: (created: Collection) => {
      qc.invalidateQueries({ queryKey: queryKeys.myCollections() });
      qc.setQueryData(queryKeys.collection(created.id), created);
    },
  });
}

export function useUpdateCollectionMutation(id: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (patch: Partial<{ name: string; description: string; is_public: boolean }>) =>
      updateCollection(id, patch),
    onSuccess: (updated) => {
      qc.setQueryData(queryKeys.collection(id), updated);
      qc.invalidateQueries({ queryKey: queryKeys.myCollections() });
    },
  });
}

export function useDeleteCollectionMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCollection(id),
    onSuccess: (_d, id) => {
      qc.removeQueries({ queryKey: queryKeys.collection(id) });
      qc.invalidateQueries({ queryKey: queryKeys.myCollections() });
    },
  });
}

export function useAddToCollectionMutation(collectionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (mediaId: string) => addToCollection(collectionId, mediaId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.collectionItems(collectionId) });
    },
  });
}

export function useRemoveFromCollectionMutation(collectionId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (mediaId: string) => removeFromCollection(collectionId, mediaId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.collectionItems(collectionId) });
    },
  });
}
