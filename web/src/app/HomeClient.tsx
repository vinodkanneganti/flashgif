"use client";

import { useState } from "react";
import { useTrending } from "@/lib/query/hooks";
import { TypeChips } from "@/components/search/TypeChips";
import { MasonryGrid } from "@/components/media/MasonryGrid";
import type { MediaSummary } from "@/lib/api/endpoints";

type MediaType = "all" | "gif" | "sticker";

export function HomeClient({ initial }: { initial: MediaSummary[] }) {
  const [type, setType] = useState<MediaType>("all");
  const apiType = type === "all" ? undefined : type;

  // Pass SSR'd data as initialData only when "all" is selected — switching the
  // chip triggers a fresh client fetch for the new type.
  const { data, isLoading } = useTrending(apiType, type === "all" ? initial : undefined);

  return (
    <div className="container mx-auto px-4 py-6 space-y-6">
      <section className="space-y-3">
        <h1 className="text-2xl font-semibold">Trending</h1>
        <TypeChips selected={type} onChange={setType} />
      </section>

      {isLoading && !data ? (
        <SkeletonGrid />
      ) : (
        <MasonryGrid items={data ?? []} />
      )}
    </div>
  );
}

function SkeletonGrid() {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
      {Array.from({ length: 12 }).map((_, i) => (
        <div key={i} className="aspect-[4/3] rounded-lg bg-muted animate-pulse" />
      ))}
    </div>
  );
}
