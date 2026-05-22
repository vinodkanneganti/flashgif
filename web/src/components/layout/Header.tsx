import Link from "next/link";
import { Suspense } from "react";
import { SearchBar } from "@/components/search/SearchBar";
import { Input } from "@/components/ui/input";
import { UserMenu } from "@/components/auth/UserMenu";
import { UploadButton } from "@/components/upload/UploadButton";
import type { Me } from "@/lib/api/auth";

export function Header({ user }: { user: Me | null }) {
  return (
    <header className="sticky top-0 z-40 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container mx-auto flex h-14 items-center gap-4 px-4">
        <Link
          href="/"
          className="font-bold text-lg bg-gradient-to-r from-primary to-accent bg-clip-text text-transparent"
        >
          FlashGif
        </Link>

        <div className="flex-1 flex justify-center">
          {/* SearchBar calls useSearchParams(); needs Suspense for App Router static prerender. */}
          <Suspense fallback={<SearchBarFallback />}>
            <SearchBar />
          </Suspense>
        </div>

        <div className="flex items-center gap-2">
          {user && <UploadButton />}
          <UserMenu initial={user} />
        </div>
      </div>
    </header>
  );
}

function SearchBarFallback() {
  return (
    <div className="relative w-full max-w-xl">
      <Input
        type="search"
        placeholder="Search GIFs and stickers…"
        disabled
        aria-hidden
        className="pl-9"
      />
    </div>
  );
}
