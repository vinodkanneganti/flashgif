import { redirect } from "next/navigation";
import { RegisterForm } from "@/components/auth/RegisterForm";
import { getCurrentUser } from "@/lib/auth/server";

export const dynamic = "force-dynamic";

export const metadata = { title: "Sign up" };

export default async function RegisterPage() {
  if (await getCurrentUser()) redirect("/");

  return (
    <div className="container mx-auto max-w-md px-4 py-12 space-y-6">
      <h1 className="text-2xl font-semibold">Create your account</h1>
      <RegisterForm />
    </div>
  );
}
