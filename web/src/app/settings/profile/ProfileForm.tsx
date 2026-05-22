"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { updateProfile, type UpdateProfileInput } from "@/lib/api/channels";

const SOCIAL_PLATFORMS = ["twitter", "instagram", "tiktok", "youtube", "github"] as const;

type Initial = {
  display_name: string;
  bio: string;
  website_url: string;
  avatar_url: string;
  banner_url: string;
  social_links: Record<string, string>;
};

export function ProfileForm({ initial }: { initial: Initial }) {
  const router = useRouter();
  const [form, setForm] = useState(initial);
  const [status, setStatus] = useState<{ ok?: boolean; error?: string }>({});
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof Initial>(key: K, value: Initial[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }
  function setSocial(platform: string, handle: string) {
    setForm((f) => ({ ...f, social_links: { ...f.social_links, [platform]: handle } }));
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setStatus({});

    // Strip blank social links so backend treats them as cleared.
    const social = Object.fromEntries(
      Object.entries(form.social_links).filter(([, v]) => v && v.trim().length > 0),
    );

    const patch: UpdateProfileInput = {
      display_name: form.display_name,
      bio: form.bio,
      website_url: form.website_url,
      avatar_url: form.avatar_url,
      banner_url: form.banner_url,
      social_links: social,
    };

    try {
      await updateProfile(patch);
      setStatus({ ok: true });
      router.refresh();
    } catch (e) {
      const msg = e instanceof ApiError
        ? `Save failed (${e.status})`
        : "Save failed";
      setStatus({ error: msg });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4" aria-label="Profile settings">
      <Field label="Display name">
        <Input value={form.display_name} maxLength={50}
               onChange={(e) => set("display_name", e.target.value)} required />
      </Field>

      <Field label="Bio" hint="Up to 4000 chars">
        <textarea
          className="w-full min-h-24 rounded-md border border-input bg-background px-3 py-2 text-sm"
          value={form.bio}
          maxLength={4000}
          onChange={(e) => set("bio", e.target.value)}
        />
      </Field>

      <Field label="Website URL">
        <Input type="url" value={form.website_url}
               onChange={(e) => set("website_url", e.target.value)} placeholder="https://…" />
      </Field>

      <Field label="Avatar URL" hint="Hosted image URL — direct upload coming later.">
        <Input type="url" value={form.avatar_url}
               onChange={(e) => set("avatar_url", e.target.value)} placeholder="https://…" />
      </Field>

      <Field label="Banner URL" hint="Hosted image URL — direct upload coming later.">
        <Input type="url" value={form.banner_url}
               onChange={(e) => set("banner_url", e.target.value)} placeholder="https://…" />
      </Field>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Social links</legend>
        {SOCIAL_PLATFORMS.map((p) => (
          <div key={p} className="grid grid-cols-[100px_1fr] items-center gap-3">
            <label htmlFor={`social-${p}`} className="text-sm capitalize">{p}</label>
            <Input
              id={`social-${p}`}
              value={form.social_links[p] ?? ""}
              onChange={(e) => setSocial(p, e.target.value)}
              placeholder="handle (no @)"
            />
          </div>
        ))}
      </fieldset>

      {status.ok && <div role="status" className="text-sm text-primary">Saved.</div>}
      {status.error && <div role="alert" className="text-sm text-destructive">{status.error}</div>}

      <Button type="submit" disabled={submitting}>
        {submitting ? "Saving…" : "Save"}
      </Button>
    </form>
  );
}

function Field({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-sm font-medium">{label}</label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
    </div>
  );
}
