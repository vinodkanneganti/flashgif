import { env } from "@/lib/env";

/**
 * Thin typed fetch wrapper. Reuses the browser/node fetch; no external client lib.
 * Adds: base URL, default JSON headers, error → ApiError mapping, optional auth.
 */

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly path: string,
    public readonly body?: unknown,
  ) {
    super(`HTTP ${status} on ${path}`);
    this.name = "ApiError";
  }
}

export type FetchOpts = RequestInit & {
  query?: Record<string, string | number | boolean | undefined | null>;
  /** Bearer token to attach. Auth slice will wire this up; null/undef = anonymous. */
  token?: string | null;
};

function buildUrl(path: string, query?: FetchOpts["query"]): string {
  const url = new URL(path.startsWith("/") ? path : `/${path}`, env.API_BASE_URL);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v === undefined || v === null) continue;
      url.searchParams.set(k, String(v));
    }
  }
  return url.toString();
}

export async function apiFetch<T>(path: string, opts: FetchOpts = {}): Promise<T> {
  const { query, token, headers, ...rest } = opts;
  const url = buildUrl(path, query);

  const res = await fetch(url, {
    ...rest,
    headers: {
      Accept: "application/json",
      ...(rest.body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    cache: rest.cache ?? "no-store",         // safe default; SSR pages can opt-in to caching
  });

  if (!res.ok) {
    let body: unknown = undefined;
    try { body = await res.json(); } catch { /* ignore */ }
    throw new ApiError(res.status, url, body);
  }

  // 204 No Content → return undefined as T
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}
