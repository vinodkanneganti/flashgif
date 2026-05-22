import { proxyToBackend } from "@/lib/auth/proxy";

export async function GET(_req: Request, { params }: { params: { id: string } }) {
  return proxyToBackend("GET", `/api/v1/collections/${params.id}`);
}
export async function PATCH(req: Request, { params }: { params: { id: string } }) {
  return proxyToBackend("PATCH", `/api/v1/collections/${params.id}`, await req.text());
}
export async function DELETE(_req: Request, { params }: { params: { id: string } }) {
  return proxyToBackend("DELETE", `/api/v1/collections/${params.id}`);
}
