"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Folder, Heart, LogOut, Settings, User as UserIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useLogoutMutation, useMe } from "@/lib/query/authHooks";
import type { Me } from "@/lib/api/auth";
import { cn } from "@/lib/utils";

export function UserMenu({ initial }: { initial: Me | null }) {
  const { data: me } = useMe(initial);
  const logout = useLogoutMutation();
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (!wrapperRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  // Logged out → show Login + Sign up.
  if (!me) {
    return (
      <nav className="flex items-center gap-2" aria-label="Account">
        <Link href="/login">
          <Button variant="ghost" size="sm">Log in</Button>
        </Link>
        <Link href="/register">
          <Button variant="default" size="sm">Sign up</Button>
        </Link>
      </nav>
    );
  }

  const initials = (me.display_name || me.username || "?")
    .split(/\s+/).map((s) => s[0]).join("").slice(0, 2).toUpperCase();

  return (
    <div ref={wrapperRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex items-center gap-2 rounded-full pl-2 pr-3 py-1 hover:bg-accent hover:text-accent-foreground transition-colors"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Account menu for ${me.display_name}`}
      >
        <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-medium">
          {initials}
        </span>
        <span className="text-sm font-medium hidden sm:inline">{me.display_name}</span>
      </button>

      {open && (
        <div
          role="menu"
          className={cn(
            "absolute right-0 mt-1 w-56 rounded-md border bg-background shadow-lg z-50",
            "overflow-hidden",
          )}
        >
          <div className="px-3 py-2 border-b">
            <div className="text-sm font-medium truncate">{me.display_name}</div>
            <div className="text-xs text-muted-foreground truncate">@{me.username}</div>
          </div>
          <Link
            href={`/channels/${me.username}`}
            className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <UserIcon className="h-4 w-4" />
            My channel
          </Link>
          <Link
            href="/favorites"
            className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <Heart className="h-4 w-4" />
            Favorites
          </Link>
          <Link
            href="/collections"
            className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <Folder className="h-4 w-4" />
            Collections
          </Link>
          <Link
            href="/settings/profile"
            className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <Settings className="h-4 w-4" />
            Settings
          </Link>
          <Link
            href="/dev"
            className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground border-t"
            role="menuitem"
            onClick={() => setOpen(false)}
          >
            <span className="w-4 inline-block text-center text-xs">{"</>"}</span>
            Developer
          </Link>
          <button
            type="button"
            onClick={() => { setOpen(false); logout.mutate(); }}
            disabled={logout.isPending}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground text-left"
            role="menuitem"
          >
            <LogOut className="h-4 w-4" />
            {logout.isPending ? "Logging out…" : "Log out"}
          </button>
        </div>
      )}
    </div>
  );
}
