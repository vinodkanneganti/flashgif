import { proxyToBackend } from "@/lib/auth/proxy";

export async function GET() {
  return proxyToBackend("GET", "/api/v1/users/me/collections");
}
