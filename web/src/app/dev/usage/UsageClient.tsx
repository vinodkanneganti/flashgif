"use client";

import { useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Button } from "@/components/ui/button";
import { useDevKeys, useDevUsage } from "@/lib/query/devHooks";

export function UsageClient() {
  const [keyId, setKeyId] = useState<string | null>(null);
  const [days, setDays] = useState<7 | 30>(30);

  const { data: keys } = useDevKeys();
  const { data: usage, isLoading } = useDevUsage(keyId, days);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <label className="text-sm font-medium">Key:</label>
        <select
          value={keyId ?? ""}
          onChange={(e) => setKeyId(e.target.value || null)}
          className="h-9 rounded-md border border-input bg-background px-3 text-sm"
        >
          <option value="">All keys</option>
          {keys?.map((k) => (
            <option key={k.id} value={k.id}>
              {k.name} ({k.prefix}…)
            </option>
          ))}
        </select>

        <div className="ml-auto flex gap-2">
          {[7, 30].map((d) => (
            <Button
              key={d}
              size="sm"
              variant={days === d ? "default" : "outline"}
              onClick={() => setDays(d as 7 | 30)}
            >
              Last {d}d
            </Button>
          ))}
        </div>
      </div>

      {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}

      {usage && (
        <>
          <p className="text-sm text-muted-foreground">
            <strong className="text-foreground">{usage.total_requests.toLocaleString()}</strong> requests
            in the last {usage.window_days} days
            {keyId ? "" : " across all keys"}.
          </p>

          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={usage.by_day} margin={{ top: 10, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                <XAxis
                  dataKey="date"
                  tickFormatter={(d) => d.slice(5)}
                  className="text-xs"
                />
                <YAxis allowDecimals={false} className="text-xs" />
                <Tooltip
                  contentStyle={{ background: "hsl(var(--background))", border: "1px solid hsl(var(--border))" }}
                  labelClassName="text-foreground"
                />
                <Bar dataKey="count" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </>
      )}
    </div>
  );
}
