"use client";

import { useState } from "react";
import { Globe, BadgeCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MasonryGrid } from "@/components/media/MasonryGrid";
import { getChannelMedia, type ChannelResponse } from "@/lib/api/channels";
import type { MediaSummary } from "@/lib/api/endpoints";
import { useQuery } from "@tanstack/react-query";

const SOCIAL_HOSTS: Record<string, string> = {
  twitter:   "https://twitter.com/",
  instagram: "https://instagram.com/",
  tiktok:    "https://tiktok.com/@",
  youtube:   "https://youtube.com/@",
  github:    "https://github.com/",
};

export function ChannelClient({ channel }: { channel: ChannelResponse }) {
  const [page, setPage] = useState(0);
  const { data: mediaPage } = useQuery({
    queryKey: ["channel", channel.username, "media", page],
    queryFn: () => getChannelMedia(channel.username, page),
    placeholderData: (prev) => prev,
  });

  return (
    <div>
      {/* Banner */}
      <div
        className="w-full h-48 md:h-64 bg-gradient-to-br from-primary/30 to-accent/30"
        style={channel.banner_url ? {
          backgroundImage: `url(${channel.banner_url})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
        } : undefined}
        aria-label="Channel banner"
      />

      <div className="container mx-auto px-4 -mt-12 pb-6">
        {/* Avatar + meta */}
        <div className="flex flex-col sm:flex-row sm:items-end gap-4">
          <div
            className="w-24 h-24 rounded-full border-4 border-background bg-muted flex items-center justify-center text-xl font-semibold shrink-0 overflow-hidden"
            style={channel.avatar_url ? {
              backgroundImage: `url(${channel.avatar_url})`,
              backgroundSize: "cover",
              backgroundPosition: "center",
            } : undefined}
            aria-label="Avatar"
          >
            {!channel.avatar_url && channel.display_name.slice(0, 2).toUpperCase()}
          </div>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-2xl font-semibold">{channel.display_name}</h1>
              {channel.verified && (
                <BadgeCheck className="h-5 w-5 text-primary" aria-label="Verified" />
              )}
              <span className="text-sm text-muted-foreground">@{channel.username}</span>
            </div>
            {channel.bio && (
              <p className="mt-1 text-sm text-muted-foreground whitespace-pre-wrap">{channel.bio}</p>
            )}
            <div className="mt-2 flex flex-wrap gap-3 text-sm">
              {channel.website_url && (
                <a
                  href={channel.website_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary hover:underline flex items-center gap-1"
                >
                  <Globe className="h-3 w-3" /> Website
                </a>
              )}
              {channel.social_links && Object.entries(channel.social_links).map(([k, v]) => {
                const host = SOCIAL_HOSTS[k];
                const handle = v.replace(/^@/, "");
                return host ? (
                  <a key={k} href={`${host}${handle}`} target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">
                    {k}/@{handle}
                  </a>
                ) : (
                  <span key={k} className="text-muted-foreground">{k}: {v}</span>
                );
              })}
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              {channel.upload_count} uploads · joined {new Date(channel.created_at).toLocaleDateString()}
            </p>
          </div>
        </div>

        <div className="mt-8 grid md:grid-cols-[1fr_280px] gap-8">
          {/* Main upload feed */}
          <section>
            <h2 className="text-lg font-semibold mb-3">Uploads</h2>
            {mediaPage && mediaPage.items.length === 0 ? (
              <p className="text-sm text-muted-foreground">No uploads yet.</p>
            ) : (
              <>
                <MasonryGrid items={(mediaPage?.items ?? []) as MediaSummary[]} />
                {mediaPage && mediaPage.total > mediaPage.size && (
                  <div className="flex justify-between items-center mt-4">
                    <span className="text-sm text-muted-foreground">
                      Page {mediaPage.page + 1} · {mediaPage.total} total
                    </span>
                    <div className="flex gap-2">
                      <Button size="sm" variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
                      <Button size="sm" variant="outline"
                        disabled={(page + 1) * mediaPage.size >= mediaPage.total}
                        onClick={() => setPage((p) => p + 1)}>Next</Button>
                    </div>
                  </div>
                )}
              </>
            )}
          </section>

          {/* Top sidebar */}
          <aside>
            <h2 className="text-lg font-semibold mb-3">Most favorited</h2>
            {channel.top_media.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nothing yet.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {channel.top_media.map((m) => (
                  <li key={m.id} className="flex justify-between gap-3">
                    <span className="truncate">{m.title}</span>
                    <span className="text-muted-foreground shrink-0">{m.favorite_count} ♥</span>
                  </li>
                ))}
              </ul>
            )}
          </aside>
        </div>
      </div>
    </div>
  );
}
