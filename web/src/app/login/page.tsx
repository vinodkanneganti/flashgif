import { redirect } from "next/navigation";
import { Suspense } from "react";
import { LoginForm } from "@/components/auth/LoginForm";
import { getCurrentUser } from "@/lib/auth/server";

export const dynamic = "force-dynamic";

export const metadata = { title: "Log in" };

export default async function LoginPage() {
  // Don't show the form to already-authenticated users.
  if (await getCurrentUser()) redirect("/");

  return (
    <div className="container mx-auto max-w-md px-4 py-12 space-y-6">
      <h1 className="text-2xl font-semibold">Log in to FlashGif</h1>
      <Suspense>
        <LoginForm />
      </Suspense>
    </div>
  );
}
