import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/auth/server";
import { UsageClient } from "./UsageClient";

export const dynamic = "force-dynamic";
export const metadata = { title: "Usage analytics" };

export default async function UsagePage() {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/dev/usage");
  return (
    <div className="container mx-auto max-w-4xl px-4 py-8 space-y-6">
      <h1 className="text-2xl font-semibold">Usage analytics</h1>
      <UsageClient />
    </div>
  );
}
