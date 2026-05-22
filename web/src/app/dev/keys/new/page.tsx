import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/auth/server";
import { NewKeyClient } from "./NewKeyClient";

export const dynamic = "force-dynamic";
export const metadata = { title: "New API key" };

export default async function NewKeyPage() {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/dev/keys/new");
  return (
    <div className="container mx-auto max-w-xl px-4 py-8 space-y-6">
      <h1 className="text-2xl font-semibold">New API key</h1>
      <NewKeyClient />
    </div>
  );
}
