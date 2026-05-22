"use client";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type MediaType = "all" | "gif" | "sticker";

const CHIPS: { label: string; value: MediaType }[] = [
  { label: "All",      value: "all" },
  { label: "GIFs",     value: "gif" },
  { label: "Stickers", value: "sticker" },
];

export function TypeChips({
  selected,
  onChange,
}: {
  selected: MediaType;
  onChange: (v: MediaType) => void;
}) {
  return (
    <div className="flex gap-2">
      {CHIPS.map((c) => (
        <Button
          key={c.value}
          variant={selected === c.value ? "default" : "outline"}
          size="sm"
          onClick={() => onChange(c.value)}
          className={cn("rounded-full px-4")}
          aria-pressed={selected === c.value}
        >
          {c.label}
        </Button>
      ))}
    </div>
  );
}
