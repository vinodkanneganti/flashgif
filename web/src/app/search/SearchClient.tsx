"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef } from "react";
import { useSearch } from "@/lib/query/hooks";
import { MasonryGrid } from "@/components/media/MasonryGrid";
import { TypeChips } from "@/components/search/TypeChips";
import { Button } from "@/components/ui/button";

type MediaType = "all" | "gif" | "sticker";
type Sort = "relevance" | "recency";

export function SearchClient() {
  const router = useRouter();
  const params = useSearchParams();

  const q     = (params?.get("q") ?? "").trim();
  const type  = (params?.get("type") ?? "all") as MediaType;
  const sort  = (params?.get("sort") ?? "relevance") as Sort;

  const apiType = type === "all" ? undefined : type;
  const { data, isFetching, isLoading, hasNextPage, fetchNextPage } = useSearch({
    q: q || undefined,
    type: apiType,
    sort,
  });

  const items = useMemo(
    () => (data?.pages.flatMap((p) => p.items) ?? []),
    [data],
  );
  const total = data?.pages[0]?.total ?? 0;

  // URL writers — preserve other params on each chip-change.
  const updateUrl = useCallback(
    (patch: Partial<{ q: string; type: MediaType; sort: Sort }>) => {
      const next = new URLSearchParams(params?.toString() ?? "");
      for (const [k, v] of Object.entries(patch)) {
        if (v === undefined || v === null || v === "") next.delete(k);
        else next.set(k, v);
      }
      router.replace(`/search?${next.toString()}`);
    },
    [router, params],
  );

  // Infinite scroll sentinel.
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (!sentinelRef.current) return;
    const obs = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting && hasNextPage && !isFetching) fetchNextPage();
    }, { rootMargin: "400px" });
    obs.observe(sentinelRef.current);
    return () => obs.disconnect();
  }, [hasNextPage, isFetching, fetchNextPage]);

  return (
    <div className="container mx-auto px-4 py-6 space-y-6">
      <header className="space-y-3">
        <h1 className="text-2xl font-semibold">
          {q ? <>Results for &ldquo;{q}&rdquo;</> : "Search"}
        </h1>
        <div className="flex flex-wrap items-center gap-4">
          <TypeChips selected={type} onChange={(v) => updateUrl({ type: v })} />
          <div className="flex gap-2">
            <Button
              size="sm"
              variant={sort === "relevance" ? "default" : "outline"}
              onClick={() => updateUrl({ sort: "relevance" })}
              className="rounded-full px-4"
            >
              Relevance
            </Button>
            <Button
              size="sm"
              variant={sort === "recency" ? "default" : "outline"}
              onClick={() => updateUrl({ sort: "recency" })}
              className="rounded-full px-4"
            >
              Recent
            </Button>
          </div>
          {data && (
            <span className="text-sm text-muted-foreground ml-auto">
              {total.toLocaleString()} result{total === 1 ? "" : "s"}
            </span>
          )}
        </div>
      </header>

      {isLoading ? (
        <div className="text-center py-16 text-muted-foreground">Searching…</div>
      ) : (
        <>
          <MasonryGrid items={items} />
          <div ref={sentinelRef} className="h-12" />
          {isFetching && hasNextPage && (
            <div className="text-center py-4 text-sm text-muted-foreground">Loading more…</div>
          )}
        </>
      )}
    </div>
  );
}
