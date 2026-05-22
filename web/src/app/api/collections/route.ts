import { proxyToBackend } from "@/lib/auth/proxy";

export async function POST(req: Request) {
  return proxyToBackend("POST", "/api/v1/collections", await req.text());
}
