/**
 * React Query key factory. Centralised so cache invalidation is type-safe
 * and we never accidentally double-cache the same data under different keys.
 */
export const queryKeys = {
  trending: (type?: string) => ["trending", type ?? "all"] as const,
  search:   (params: { q?: string; type?: string; sort?: string; page?: number; size?: number }) =>
              ["search", params] as const,
  suggestions: (q: string) => ["suggestions", q.toLowerCase().trim()] as const,
  me:       () => ["me"] as const,
  myFavorites: (page?: number) => ["favorites", "me", page ?? 0] as const,
  myFavoriteIds: () => ["favorites", "me", "ids"] as const,
  myCollections: () => ["collections", "me"] as const,
  collection: (id: string) => ["collection", id] as const,
  collectionItems: (id: string, page?: number) => ["collection-items", id, page ?? 0] as const,
  devKeys: () => ["dev", "keys"] as const,
  devUsage: (keyId: string | null, days: number) => ["dev", "usage", keyId ?? "all", days] as const,
};
