import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { setSessionCookies, type AuthResponse } from "@/lib/auth/session";

export async function POST(req: Request) {
  const body = await req.text();

  const upstream = await fetch(`${env.API_BASE_URL}/api/v1/auth/register`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "User-Agent":   req.headers.get("user-agent") ?? "flashgif-web",
      "X-Forwarded-For": req.headers.get("x-forwarded-for")
                       ?? req.headers.get("x-real-ip") ?? "",
    },
    body,
    cache: "no-store",
  });

  if (!upstream.ok) {
    // Surface backend's JSON error body if present so the form can show useful messages.
    let detail: unknown = undefined;
    try { detail = await upstream.json(); } catch { /* ignore */ }
    return NextResponse.json(
      { error: upstream.statusText || "Registration failed", detail },
      { status: upstream.status },
    );
  }

  const auth = (await upstream.json()) as AuthResponse;
  setSessionCookies(auth);
  return NextResponse.json({ ok: true }, { status: 201 });
}
