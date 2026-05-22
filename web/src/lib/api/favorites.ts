"use client";

import { authedFetch } from "./authed";
import type { MediaSummary } from "./endpoints";

export type Favorite = { media_id: string; created_at: string };
export type Paged<T> = { items: T[]; page: number; size: number; total: number };

export type Collection = {
  id: string;
  owner_id: string;
  name: string;
  description: string | null;
  is_public: boolean;
  created_at: string;
  updated_at: string;
};
export type CollectionItem = { media_id: string; added_at: string };

// --- favorites ---

export function addFavorite(media_id: string) {
  return authedFetch<void>("/api/favorites", {
    method: "POST",
    body: JSON.stringify({ media_id }),
  });
}
export function removeFavorite(mediaId: string) {
  return authedFetch<void>(`/api/favorites/${mediaId}`, { method: "DELETE" });
}
export function listMyFavorites(page = 0, size = 20) {
  return authedFetch<Paged<Favorite>>(
    `/api/users/me/favorites?page=${page}&size=${size}`,
  );
}

// --- collections ---

export function listMyCollections() {
  return authedFetch<Collection[]>("/api/users/me/collections");
}
export function createCollection(input: { name: string; description?: string; is_public?: boolean }) {
  return authedFetch<Collection>("/api/collections", {
    method: "POST",
    body: JSON.stringify(input),
  });
}
export function getCollection(id: string) {
  return authedFetch<Collection>(`/api/collections/${id}`);
}
export function updateCollection(
  id: string,
  patch: Partial<{ name: string; description: string; is_public: boolean }>,
) {
  return authedFetch<Collection>(`/api/collections/${id}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
}
export function deleteCollection(id: string) {
  return authedFetch<void>(`/api/collections/${id}`, { method: "DELETE" });
}
export function listCollectionItems(id: string, page = 0, size = 20) {
  return authedFetch<Paged<CollectionItem>>(
    `/api/collections/${id}/items?page=${page}&size=${size}`,
  );
}
export function addToCollection(id: string, media_id: string) {
  return authedFetch<void>(`/api/collections/${id}/items`, {
    method: "POST",
    body: JSON.stringify({ media_id }),
  });
}
export function removeFromCollection(id: string, mediaId: string) {
  return authedFetch<void>(`/api/collections/${id}/items/${mediaId}`, { method: "DELETE" });
}

// Re-export for convenience
export type { MediaSummary };
