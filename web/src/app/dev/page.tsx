import Link from "next/link";
import { redirect } from "next/navigation";
import { getCurrentUser } from "@/lib/auth/server";
import { DevDashboard } from "./DevDashboard";

export const dynamic = "force-dynamic";
export const metadata = { title: "Developer" };

export default async function DevPage() {
  const user = await getCurrentUser();
  if (!user) redirect("/login?next=/dev");

  return (
    <div className="container mx-auto px-4 py-6 space-y-8 max-w-4xl">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold">Developer dashboard</h1>
        <p className="text-sm text-muted-foreground">
          Issue API keys to embed FlashGif search in your apps.{" "}
          <a href="http://localhost:8080/swagger-ui.html" className="text-primary hover:underline" target="_blank" rel="noopener noreferrer">
            Browse the API
          </a>.
        </p>
        <div className="flex gap-2 pt-2">
          <Link href="/dev/keys/new" className="text-sm text-primary hover:underline">+ New API key</Link>
          <span className="text-muted-foreground">·</span>
          <Link href="/dev/usage" className="text-sm text-primary hover:underline">Usage analytics →</Link>
        </div>
      </header>
      <DevDashboard />
    </div>
  );
}
