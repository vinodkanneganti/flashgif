"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { getSuggestions, getTrending, search, type MediaSummary, type SearchResponse } from "@/lib/api/endpoints";
import { queryKeys } from "./keys";

/** Trending list — cached 60s on backend; we keep our own 60s staleTime so re-mounts don't refetch. */
export function useTrending(type?: "gif" | "sticker", initialData?: MediaSummary[]) {
  return useQuery({
    queryKey: queryKeys.trending(type),
    queryFn:  () => getTrending(type),
    staleTime: 60_000,
    initialData,
  });
}

/** Search with infinite pagination. Each page is a backend SearchResponse. */
export function useSearch(params: {
  q?: string;
  type?: "gif" | "sticker";
  sort?: "relevance" | "recency";
  size?: number;
}) {
  const size = params.size ?? 20;
  return useInfiniteQuery<SearchResponse, Error>({
    queryKey: queryKeys.search({ ...params, size }),
    queryFn: ({ pageParam = 0 }) =>
      search({ ...params, page: pageParam as number, size }),
    initialPageParam: 0,
    getNextPageParam: (last) => {
      const nextPage = last.page + 1;
      const fetched  = (last.page + 1) * last.size;
      return fetched < last.total ? nextPage : undefined;
    },
    staleTime: 30_000,
    enabled: !!params.q || params.sort === "recency",   // empty q + relevance → backend just returns trending; let useTrending handle that
  });
}

/** Autocomplete — debounced upstream by the SearchBar; cached per prefix. */
export function useSuggestions(q: string) {
  const trimmed = q.trim();
  return useQuery({
    queryKey: queryKeys.suggestions(trimmed),
    queryFn:  () => getSuggestions(trimmed),
    enabled:  trimmed.length >= 2,
    staleTime: 5 * 60_000,
  });
}
