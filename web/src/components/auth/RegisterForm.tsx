"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import { registerSchema, type RegisterValues } from "@/lib/auth/schemas";
import { useRegisterMutation } from "@/lib/query/authHooks";

export function RegisterForm() {
  const router = useRouter();
  const mutation = useRegisterMutation();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: "", username: "", password: "", display_name: "" },
  });

  async function onSubmit(values: RegisterValues) {
    try {
      await mutation.mutateAsync(values);
      router.push("/");
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        const detail = e.body as { detail?: { message?: string } } | undefined;
        const msg = detail?.detail?.message ?? "Email or username already in use";
        setError("root", { message: msg });
      } else {
        setError("root", { message: "Registration failed — try again" });
      }
    }
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      noValidate
      className="space-y-4"
      aria-label="Sign up"
    >
      <div className="space-y-1">
        <label htmlFor="display_name" className="text-sm font-medium">Display name</label>
        <Input id="display_name" type="text" autoComplete="name" {...register("display_name")} />
        {errors.display_name && (
          <p className="text-xs text-destructive">{errors.display_name.message}</p>
        )}
      </div>

      <div className="space-y-1">
        <label htmlFor="username" className="text-sm font-medium">Username</label>
        <Input id="username" type="text" autoComplete="username" {...register("username")} />
        {errors.username && (
          <p className="text-xs text-destructive">{errors.username.message}</p>
        )}
        <p className="text-xs text-muted-foreground">
          Public handle for your channel URL. 3–30 chars, letters/digits/underscore.
        </p>
      </div>

      <div className="space-y-1">
        <label htmlFor="email" className="text-sm font-medium">Email</label>
        <Input id="email" type="email" autoComplete="email" {...register("email")} />
        {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
      </div>

      <div className="space-y-1">
        <label htmlFor="password" className="text-sm font-medium">Password</label>
        <Input
          id="password"
          type="password"
          autoComplete="new-password"
          {...register("password")}
        />
        {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
        <p className="text-xs text-muted-foreground">At least 12 characters.</p>
      </div>

      {errors.root && (
        <div role="alert" className="text-sm text-destructive">{errors.root.message}</div>
      )}

      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? "Creating account…" : "Sign up"}
      </Button>

      <p className="text-sm text-muted-foreground text-center">
        Already have an account?{" "}
        <Link href="/login" className="text-primary hover:underline">Log in</Link>
      </p>
    </form>
  );
}
