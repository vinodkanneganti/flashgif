import { proxyToBackend } from "@/lib/auth/proxy";

export async function PATCH(req: Request) {
  return proxyToBackend("PATCH", "/api/v1/channels/profile", await req.text());
}
