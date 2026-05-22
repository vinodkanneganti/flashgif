import { proxyToBackend } from "@/lib/auth/proxy";

export async function GET() {
  return proxyToBackend("GET", "/api/v1/auth/developer/keys");
}
export async function POST(req: Request) {
  return proxyToBackend("POST", "/api/v1/auth/developer/keys", await req.text());
}
