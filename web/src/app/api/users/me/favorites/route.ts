import { proxyToBackend } from "@/lib/auth/proxy";

export async function GET(req: Request) {
  const url = new URL(req.url);
  return proxyToBackend(
    "GET",
    `/api/v1/users/me/favorites?${url.searchParams.toString()}`,
  );
}
