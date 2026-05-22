import { proxyToBackend } from "@/lib/auth/proxy";

export async function DELETE(_req: Request, { params }: { params: { mediaId: string } }) {
  return proxyToBackend("DELETE", `/api/v1/favorites/${params.mediaId}`);
}
