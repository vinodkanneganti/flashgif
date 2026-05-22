"use client";

import { authedFetch } from "./authed";

export type Me = {
  id: string;
  email: string;
  username: string;
  display_name: string;
  status: string;
  created_at: string;
};

export type LoginInput = {
  email: string;
  password: string;
};

export type RegisterInput = {
  email: string;
  username: string;
  password: string;
  display_name: string;
};

/** GET /api/users/me (same-origin Next.js proxy). */
export function fetchMe() {
  return authedFetch<Me>("/api/users/me");
}

export function login(input: LoginInput) {
  return authedFetch<{ ok: true }>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function register(input: RegisterInput) {
  return authedFetch<{ ok: true }>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function logout() {
  return authedFetch<void>("/api/auth/logout", { method: "POST" });
}
