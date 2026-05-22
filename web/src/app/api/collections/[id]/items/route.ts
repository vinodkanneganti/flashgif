import { proxyToBackend } from "@/lib/auth/proxy";

export async function GET(req: Request, { params }: { params: { id: string } }) {
  const url = new URL(req.url);
  return proxyToBackend(
    "GET",
    `/api/v1/collections/${params.id}/items?${url.searchParams.toString()}`,
  );
}
export async function POST(req: Request, { params }: { params: { id: string } }) {
  return proxyToBackend(
    "POST",
    `/api/v1/collections/${params.id}/items`,
    await req.text(),
  );
}
