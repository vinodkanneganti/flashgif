import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { setSessionCookies, type AuthResponse } from "@/lib/auth/session";

export async function POST(req: Request) {
  const body = await req.text();

  const upstream = await fetch(`${env.API_BASE_URL}/api/v1/auth/login`, {
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
    return NextResponse.json(
      { error: upstream.statusText || "Login failed" },
      { status: upstream.status },
    );
  }

  const auth = (await upstream.json()) as AuthResponse;
  setSessionCookies(auth);
  return NextResponse.json({ ok: true });
}
