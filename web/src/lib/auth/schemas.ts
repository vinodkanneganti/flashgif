import { z } from "zod";

/**
 * Constraints mirror the backend (`RegisterRequest`/`LoginRequest` in users module).
 * Keep this file in sync if the backend ever loosens or tightens rules.
 */

const email       = z.string().trim().email("Enter a valid email");
const password    = z.string().min(12, "At least 12 characters");
const username    = z.string().regex(/^[a-zA-Z0-9_]{3,30}$/, "3–30 chars, letters/digits/underscore only");
const displayName = z.string().trim().min(1, "Required").max(50, "Max 50 characters");

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Required"),    // be tolerant on login (backend is the source of truth)
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  email,
  username,
  password,
  display_name: displayName,
});
export type RegisterValues = z.infer<typeof registerSchema>;
