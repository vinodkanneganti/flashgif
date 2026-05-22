import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/auth/server";
import { CollectionsClient } from "./CollectionsClient";

export const dynamic = "force-dynamic";
export const metadata = { title: "Your collections" };

export default async function CollectionsPage() {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/collections");
  return <CollectionsClient />;
}
