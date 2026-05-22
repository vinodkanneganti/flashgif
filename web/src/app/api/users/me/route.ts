import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { readAccessToken } from "@/lib/auth/server";

export async function GET() {
  const token = readAccessToken();
  if (!token) {
    return NextResponse.json({ error: "Unauthenticated" }, { status: 401 });
  }

  const upstream = await fetch(`${env.API_BASE_URL}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}` },
    cache:   "no-store",
  });

  if (!upstream.ok) {
    return NextResponse.json(
      { error: upstream.statusText || "Failed to load profile" },
      { status: upstream.status },
    );
  }

  return NextResponse.json(await upstream.json());
}
