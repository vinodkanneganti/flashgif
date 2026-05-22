"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createKey, getUsage, listKeys, revokeKey } from "@/lib/api/developer";
import { queryKeys } from "./keys";

export function useDevKeys() {
  return useQuery({ queryKey: queryKeys.devKeys(), queryFn: listKeys });
}
export function useCreateKeyMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => createKey(name),
    onSuccess: () => { qc.invalidateQueries({ queryKey: queryKeys.devKeys() }); },
  });
}
export function useRevokeKeyMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => revokeKey(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: queryKeys.devKeys() }); },
  });
}
export function useDevUsage(keyId: string | null, days: number) {
  return useQuery({
    queryKey: queryKeys.devUsage(keyId, days),
    queryFn: () => getUsage(keyId, days),
  });
}
