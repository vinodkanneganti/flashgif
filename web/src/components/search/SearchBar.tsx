"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Search as SearchIcon } from "lucide-react";
import { Input } from "@/components/ui/input";
import { useSuggestions } from "@/lib/query/hooks";
import { cn } from "@/lib/utils";

/**
 * Header-mounted search. Local input → debounced suggestions dropdown →
 * Enter / suggestion-click → router.push(/search?q=...).
 */
export function SearchBar() {
  const router = useRouter();
  const params = useSearchParams();
  const [value, setValue] = useState(params?.get("q") ?? "");
  const [debounced, setDebounced] = useState(value);
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  // Debounce 200ms → suggestion query
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), 200);
    return () => clearTimeout(t);
  }, [value]);

  const { data: suggestions } = useSuggestions(debounced);

  // Close dropdown on outside click
  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (!wrapperRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  function submit(q: string) {
    const trimmed = q.trim();
    if (!trimmed) return;
    setOpen(false);
    router.push(`/search?q=${encodeURIComponent(trimmed)}`);
  }

  return (
    <div ref={wrapperRef} className="relative w-full max-w-xl">
      <form
        onSubmit={(e) => { e.preventDefault(); submit(value); }}
        className="relative"
      >
        <SearchIcon
          className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
          aria-hidden
        />
        <Input
          type="search"
          placeholder="Search GIFs and stickers…"
          value={value}
          onChange={(e) => { setValue(e.target.value); setOpen(true); }}
          onFocus={() => setOpen(true)}
          className="pl-9"
          aria-label="Search"
        />
      </form>

      {open && suggestions && suggestions.items.length > 0 && (
        <div
          className={cn(
            "absolute left-0 right-0 mt-1 bg-background border rounded-md shadow-lg",
            "max-h-72 overflow-y-auto z-50",
          )}
        >
          {suggestions.items.map((s) => (
            <button
              key={`${s.source}:${s.text}`}
              type="button"
              onClick={() => submit(s.text)}
              className="block w-full text-left px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
            >
              {s.text}
              <span className="ml-2 text-xs text-muted-foreground">{s.source}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
