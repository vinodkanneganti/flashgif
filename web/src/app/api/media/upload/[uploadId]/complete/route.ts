import { NextResponse } from "next/server";
import { env } from "@/lib/env";
import { readAccessToken } from "@/lib/auth/server";

export async function POST(_req: Request, { params }: { params: { uploadId: string } }) {
  const token = readAccessToken();
  if (!token) return NextResponse.json({ error: "Unauthenticated" }, { status: 401 });

  const upstream = await fetch(
    `${env.API_BASE_URL}/api/v1/media/upload/${params.uploadId}/complete`,
    { method: "POST", headers: { Authorization: `Bearer ${token}` }, cache: "no-store" },
  );

  if (upstream.status === 204) return new NextResponse(null, { status: 202 });
  return NextResponse.json(await upstream.json().catch(() => ({})), { status: upstream.status });
}
