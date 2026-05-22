"use client";

import { authedFetch } from "./authed";

export type DeveloperKey = {
  id: string;
  name: string;
  prefix: string;
  status: "active" | "revoked";
  created_at: string;
  last_used_at: string | null;
  revoked_at: string | null;
};
export type IssuedKey = DeveloperKey & {
  /** Raw key, shown ONCE at creation. */
  key: string;
};

export type UsageAnalytics = {
  key_id: string | null;
  window_days: number;
  total_requests: number;
  by_day: { date: string; count: number }[];
};

export function listKeys() {
  return authedFetch<DeveloperKey[]>("/api/auth/developer/keys");
}
export function createKey(name: string) {
  return authedFetch<IssuedKey>("/api/auth/developer/keys", {
    method: "POST",
    body: JSON.stringify({ name }),
  });
}
export function revokeKey(id: string) {
  return authedFetch<void>(`/api/auth/developer/keys/${id}`, { method: "DELETE" });
}

export function getUsage(keyId: string | null, days: number) {
  const q = new URLSearchParams({ days: String(days) });
  if (keyId) q.set("key_id", keyId);
  return authedFetch<UsageAnalytics>(`/api/usage/analytics?${q.toString()}`);
}
