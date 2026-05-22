"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { loginSchema, type LoginValues } from "@/lib/auth/schemas";
import { useLoginMutation } from "@/lib/query/authHooks";

const IS_LOCAL = process.env.NEXT_PUBLIC_ENV === "local";

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params?.get("next") || "/";
  const mutation = useLoginMutation();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  async function onSubmit(values: LoginValues) {
    try {
      await mutation.mutateAsync(values);
      router.push(next);
    } catch (e) {
      const msg = e instanceof ApiError && e.status === 401
        ? "Invalid email or password"
        : "Login failed — try again";
      setError("root", { message: msg });
    }
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      noValidate
      className="space-y-4"
      aria-label="Log in"
    >
      <div className="space-y-1">
        <label htmlFor="email" className="text-sm font-medium">Email</label>
        <Input id="email" type="email" autoComplete="email" {...register("email")} />
        {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
      </div>

      <div className="space-y-1">
        <label htmlFor="password" className="text-sm font-medium">Password</label>
        <Input id="password" type="password" autoComplete="current-password" {...register("password")} />
        {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
      </div>

      {errors.root && (
        <div role="alert" className="text-sm text-destructive">{errors.root.message}</div>
      )}

      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? "Logging in…" : "Log in"}
      </Button>

      <p className="text-sm text-muted-foreground text-center">
        No account?{" "}
        <Link href="/register" className="text-primary hover:underline">Sign up</Link>
      </p>

      {IS_LOCAL && (
        <div
          className="rounded-md border border-dashed p-3 text-xs text-muted-foreground"
          aria-label="Dev credentials hint"
        >
          <span className="font-medium text-foreground">Dev:</span>{" "}
          <code>dev@flashgif.example</code> / <code>dev-password</code>
        </div>
      )}
    </form>
  );
}
