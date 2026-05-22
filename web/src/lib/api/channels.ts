/**
 * Mixed module: `getChannel`/`getChannelMedia` are pure fetches usable from
 * server components. `updateProfile` is client-only (cookie-bound via authedFetch).
 */
import { authedFetch } from "./authed";
import { apiFetch } from "./client";
import type { MediaSummary } from "./endpoints";

export type ChannelResponse = {
  username: string;
  display_name: string;
  bio: string | null;
  website_url: string | null;
  avatar_url: string | null;
  banner_url: string | null;
  social_links: Record<string, string> | null;
  verified: boolean;       // backend boolean field is_verified → wire "verified" (Lombok+Jackson)
  upload_count: number;
  top_media: MediaSummary[];
  created_at: string;
};

export type UpdateProfileInput = {
  display_name?: string;
  bio?: string;
  website_url?: string;
  avatar_url?: string;
  banner_url?: string;
  social_links?: Record<string, string>;
};

/** Public read — direct to Spring. */
export function getChannel(username: string) {
  return apiFetch<ChannelResponse>(`/api/v1/channels/${username}`);
}

export function getChannelMedia(username: string, page = 0, size = 20) {
  return apiFetch<{ items: MediaSummary[]; page: number; size: number; total: number }>(
    `/api/v1/channels/${username}/media`,
    { query: { page, size } },
  );
}

/** Owner-only — proxies through Next.js Route Handler with cookie. */
export function updateProfile(patch: UpdateProfileInput) {
  return authedFetch<ChannelResponse>("/api/channels/profile", {
    method: "PATCH",
    body: JSON.stringify(patch),
  });
}
