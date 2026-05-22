"use client";

import { Button } from "@/components/ui/button";
import { useDevKeys, useRevokeKeyMutation } from "@/lib/query/devHooks";

export function DevDashboard() {
  const { data: keys, isLoading } = useDevKeys();
  const revoke = useRevokeKeyMutation();

  if (isLoading) return <p className="text-sm text-muted-foreground">Loading…</p>;

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold">API keys</h2>

      {keys && keys.length === 0 && (
        <p className="text-sm text-muted-foreground">No keys yet.</p>
      )}

      {keys && keys.length > 0 && (
        <div className="rounded-md border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted">
              <tr className="text-left">
                <th className="px-3 py-2 font-medium">Name</th>
                <th className="px-3 py-2 font-medium">Prefix</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Last used</th>
                <th className="px-3 py-2 font-medium">Created</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody>
              {keys.map((k) => (
                <tr key={k.id} className="border-t">
                  <td className="px-3 py-2">{k.name}</td>
                  <td className="px-3 py-2"><code>{k.prefix}…</code></td>
                  <td className="px-3 py-2">
                    <span className={k.status === "active" ? "text-primary" : "text-muted-foreground"}>
                      {k.status}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">
                    {k.last_used_at ? new Date(k.last_used_at).toLocaleString() : "—"}
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">
                    {new Date(k.created_at).toLocaleDateString()}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {k.status === "active" && (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={async () => {
                          if (!confirm(`Revoke "${k.name}"? Apps using it will start receiving 401.`)) return;
                          await revoke.mutateAsync(k.id);
                        }}
                      >
                        Revoke
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
