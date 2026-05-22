"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { fetchMe, login, logout, register, type LoginInput, type Me, type RegisterInput } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { queryKeys } from "./keys";

/**
 * Current user. SSR seeds this via QueryProvider; on the client we keep it
 * fresh on window focus + after auth mutations. A 401 means "logged out" —
 * not an error, so we swallow it to null.
 */
export function useMe(initialData?: Me | null) {
  return useQuery<Me | null>({
    queryKey: queryKeys.me(),
    queryFn: async () => {
      try {
        return await fetchMe();
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    initialData,
    staleTime: 60_000,
    refetchOnWindowFocus: true,
  });
}

export function useLoginMutation() {
  const qc = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: (input: LoginInput) => login(input),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: queryKeys.me() });
      router.refresh();             // re-runs server components (Header SSR)
    },
  });
}

export function useRegisterMutation() {
  const qc = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: (input: RegisterInput) => register(input),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: queryKeys.me() });
      router.refresh();
    },
  });
}

export function useLogoutMutation() {
  const qc = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: () => logout(),
    onSuccess: async () => {
      qc.setQueryData(queryKeys.me(), null);
      await qc.invalidateQueries({ queryKey: queryKeys.me() });
      router.refresh();
    },
  });
}
