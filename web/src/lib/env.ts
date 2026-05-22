/**
 * Typed env access. Throws at module load if required vars are missing —
 * fail loud, not silent.
 */
function required(name: string, fallback?: string): string {
  const v = process.env[name] ?? fallback;
  if (!v) throw new Error(`Missing required env var: ${name}`);
  return v;
}

export const env = {
  /** Backend base URL, e.g. http://localhost:8080 (no trailing slash). */
  API_BASE_URL: required("NEXT_PUBLIC_API_BASE_URL", "http://localhost:8080"),
};
