import { proxyToBackend } from "@/lib/auth/proxy";

export async function DELETE(
  _req: Request,
  { params }: { params: { id: string; mediaId: string } },
) {
  return proxyToBackend(
    "DELETE",
    `/api/v1/collections/${params.id}/items/${params.mediaId}`,
  );
}
