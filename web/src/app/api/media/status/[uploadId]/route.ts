import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { readAccessToken } from "@/lib/auth/server";

export async function GET(_req: Request, { params }: { params: { uploadId: string } }) {
  const token = readAccessToken();
  if (!token) return NextResponse.json({ error: "Unauthenticated" }, { status: 401 });

  const upstream = await fetch(
    `${env.API_BASE_URL}/api/v1/media/status/${params.uploadId}`,
    { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" },
  );

  return NextResponse.json(await upstream.json(), { status: upstream.status });
}
