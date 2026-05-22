import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { readAccessToken } from "@/lib/auth/server";

export async function POST(req: Request) {
  const token = readAccessToken();
  if (!token) return NextResponse.json({ error: "Unauthenticated" }, { status: 401 });

  const upstream = await fetch(`${env.API_BASE_URL}/api/v1/media/metadata`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: await req.text(),
    cache: "no-store",
  });

  return NextResponse.json(await upstream.json(), { status: upstream.status });
}
