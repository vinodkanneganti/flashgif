"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { queryKeys } from "./keys";
import type { Me } from "@/lib/api/auth";

/**
 * Wraps client subtree with a per-browser-tab QueryClient. Created with `useState`
 * so it's stable across re-renders but reinitialised per tab (important for SSR).
 *
 * Optional `seed` lets server components pre-populate caches (e.g. the SSR-fetched
 * current user) so client hooks return the right thing on first render.
 */
export function QueryProvider({
  children,
  seed,
}: {
  children: ReactNode;
  seed?: { me: Me | null };
}) {
  const [client] = useState(() => {
    const c = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          refetchOnWindowFocus: false,
          retry: 1,
        },
      },
    });
    if (seed) c.setQueryData(queryKeys.me(), seed.me);
    return c;
  });

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
