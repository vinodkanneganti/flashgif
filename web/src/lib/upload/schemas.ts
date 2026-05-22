import { z } from "zod";

/**
 * Web-side upload allow-list. Narrower than the backend's, which still accepts
 * WebP — but Homebrew FFmpeg doesn't link libwebp by default so the *decoder*
 * fails too (Bug 8 was the encoder). Surfacing the limit at the dropzone gives
 * a clear inline error instead of a confusing transcode failure 30s later.
 * Re-enable WebP once the deploy target's FFmpeg ships with --enable-libwebp.
 */
export const ALLOWED_TYPES = [
  "video/mp4",
  "video/webm",
  "video/quicktime",
  "image/gif",
] as const;

export const MAX_SIZE_BYTES = 100 * 1024 * 1024;   // 100 MB

export function fileValidationError(file: File): string | null {
  if (file.size > MAX_SIZE_BYTES) return "File is over 100 MB.";
  if (!ALLOWED_TYPES.includes(file.type as (typeof ALLOWED_TYPES)[number])) {
    return `Unsupported type: ${file.type || "unknown"}. Allowed: GIF, MP4, WebM, MOV.`;
  }
  return null;
}

export const metadataSchema = z.object({
  title: z.string().trim().min(1, "Required").max(200, "Max 200 characters"),
  description: z.string().trim().max(2000, "Max 2000 characters").optional(),
  type: z.enum(["gif", "sticker"]),
  content_rating: z.enum(["g", "pg", "pg13", "r"]),
  tagsRaw: z.string()                                     // comma-separated, parsed to array on submit
    .max(2000)
    .optional(),
});
export type MetadataValues = z.infer<typeof metadataSchema>;

export function parseTags(raw: string | undefined): string[] {
  if (!raw) return [];
  return [...new Set(
    raw.split(",")
       .map((t) => t.trim().toLowerCase())
       .filter((t) => t.length > 0 && t.length <= 64),
  )].slice(0, 20);
}
