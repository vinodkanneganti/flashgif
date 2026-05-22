"use client";

import Link from "next/link";
import { useState } from "react";
import { Copy, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { useCreateKeyMutation } from "@/lib/query/devHooks";
import type { IssuedKey } from "@/lib/api/developer";

export function NewKeyClient() {
  const [name, setName] = useState("");
  const [issued, setIssued] = useState<IssuedKey | null>(null);
  const [error, setError] = useState<string | null>(null);
  const create = useCreateKeyMutation();

  if (issued) return <IssuedKeyDisplay issued={issued} />;

  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setError(null);
        try {
          const k = await create.mutateAsync(name.trim());
          setIssued(k);
        } catch (err) {
          setError(err instanceof ApiError ? `Failed (${err.status})` : "Failed");
        }
      }}
      className="space-y-3"
      aria-label="Create API key"
    >
      <div className="space-y-1">
        <label htmlFor="key-name" className="text-sm font-medium">Name</label>
        <Input
          id="key-name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="My messaging app"
          required maxLength={100}
        />
        <p className="text-xs text-muted-foreground">
          A description for you — clients never see the name, only the prefix.
        </p>
      </div>

      {error && <div role="alert" className="text-sm text-destructive">{error}</div>}

      <div className="flex gap-2">
        <Button type="submit" disabled={create.isPending || !name.trim()}>
          {create.isPending ? "Creating…" : "Create key"}
        </Button>
        <Link href="/dev">
          <Button variant="outline" type="button">Cancel</Button>
        </Link>
      </div>
    </form>
  );
}

function IssuedKeyDisplay({ issued }: { issued: IssuedKey }) {
  const [copied, setCopied] = useState(false);

  return (
    <div className="space-y-4">
      <div className="rounded-md border border-primary/50 bg-primary/5 p-4 space-y-3">
        <h2 className="font-semibold">Save this key now</h2>
        <p className="text-sm text-muted-foreground">
          This is the only time we'll show the full key. Store it somewhere safe.
        </p>

        <div className="flex items-center gap-2">
          <code className="flex-1 p-2 rounded bg-background border text-sm break-all">
            {issued.key}
          </code>
          <Button
            variant="outline"
            size="icon"
            type="button"
            onClick={async () => {
              await navigator.clipboard.writeText(issued.key);
              setCopied(true);
              setTimeout(() => setCopied(false), 2000);
            }}
            aria-label="Copy to clipboard"
          >
            {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
          </Button>
        </div>

        <div className="text-xs text-muted-foreground">
          Name: {issued.name} · Prefix: <code>{issued.prefix}…</code>
        </div>
      </div>

      <Link href="/dev">
        <Button>Done</Button>
      </Link>
    </div>
  );
}
