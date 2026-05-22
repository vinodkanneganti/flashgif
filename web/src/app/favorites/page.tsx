import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/auth/server";
import { FavoritesClient } from "./FavoritesClient";

export const dynamic = "force-dynamic";
export const metadata = { title: "Your favorites" };

export default async function FavoritesPage() {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/favorites");
  return <FavoritesClient />;
}
