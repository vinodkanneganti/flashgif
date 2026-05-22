import "server-only";
import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { readAccessToken } from "@/lib/auth/server";

/**
 * Shared helper for Route Handlers that just proxy an authed call to Spring.
 * Forwards method + body + Authorization, returns the upstream response
 * mostly verbatim. 204 stays 204; JSON stays JSON.
 */
export async function proxyToBackend(
  method: string,
  upstreamPath: string,
  body?: string,
): Promise<NextResponse> {
  const token = readAccessToken();
  if (!token) {
    return NextResponse.json({ error: "Unauthenticated" }, { status: 401 });
  }

  const upstream = await fetch(`${env.API_BASE_URL}${upstreamPath}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body,
    cache: "no-store",
  });

  if (upstream.status === 204 || upstream.status === 202) {
    return new NextResponse(null, { status: upstream.status });
  }
  const text = await upstream.text();
  return new NextResponse(text, {
    status: upstream.status,
    headers: { "Content-Type": upstream.headers.get("Content-Type") ?? "application/json" },
  });
}
