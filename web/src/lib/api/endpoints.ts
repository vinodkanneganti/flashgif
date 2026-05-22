import { apiFetch } from "./client";

/**
 * Slice-1 response shapes — mirrors the backend DTOs in
 * `media/api/dto/MediaSummary.java`, `search/api/dto/SearchResponse.java`,
 * `search/api/dto/SuggestionsResponse.java`.
 *
 * Once `pnpm gen:api` is run against a live backend, prefer the generated
 * types in ./types.ts over these hand-written ones.
 */

/**
 * Wire format: snake_case (backend has
 * `spring.jackson.property-naming-strategy: SNAKE_CASE`). Matches the
 * `@Schema(name = ...)` annotations in the OpenAPI spec.
 */
export type MediaSummary = {
  id: string;
  title: string;
  tags: string[];
  type: "gif" | "sticker";
  content_rating: "g" | "pg" | "pg13" | "r";
  view_count: number;
  favorite_count: number;
  width: number | null;
  height: number | null;
  rendition_urls: {
    gif?: string;
    mp4?: string;
    webp?: string;
    poster?: string;
  } | null;
  created_at: string;
};

export type SearchResponse = {
  items: MediaSummary[];
  page: number;
  size: number;
  total: number;
  took_ms: number;
};

export type Suggestion = { text: string; source: "title" | "tag" };
export type SuggestionsResponse = { items: Suggestion[] };

// ---------------- endpoints ----------------

export function getTrending(type?: "gif" | "sticker") {
  return apiFetch<MediaSummary[]>("/api/v1/trending", { query: { type } });
}

export function search(params: {
  q?: string;
  type?: "gif" | "sticker";
  sort?: "relevance" | "recency";
  page?: number;
  size?: number;
}) {
  return apiFetch<SearchResponse>("/api/v1/search", { query: params });
}

export function getSuggestions(q: string) {
  return apiFetch<SuggestionsResponse>("/api/v1/search/suggestions", { query: { q } });
}
