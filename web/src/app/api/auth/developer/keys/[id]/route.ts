import { proxyToBackend } from "@/lib/auth/proxy";

export async function DELETE(_req: Request, { params }: { params: { id: string } }) {
  return proxyToBackend("DELETE", `/api/v1/auth/developer/keys/${params.id}`);
}
