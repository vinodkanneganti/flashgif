"use client";

import { authedFetch } from "./authed";

export type UploadReserveRequest = {
  filename: string;
  content_type: string;
  size: number;
};
export type UploadReserveResponse = {
  upload_id: string;
  presigned_url: string;
  expires_at: string;
};

export type UploadStatus =
  | "AWAITING_UPLOAD"
  | "UPLOADED"
  | "PROCESSING"
  | "READY"
  | "PUBLISHED"
  | "FAILED";

export type UploadStatusResponse = {
  upload_id: string;
  status: UploadStatus;
  rendition_urls?: Record<string, string> | null;
  width?: number | null;
  height?: number | null;
  failure_reason?: string | null;
};

export type MetadataRequest = {
  upload_id: string;
  title: string;
  description?: string;
  type: "gif" | "sticker";
  content_rating: "g" | "pg" | "pg13" | "r";
  tags: string[];
};

export type PublishedMedia = {
  media_id: string;
  upload_id: string;
};

export function reserveUpload(req: UploadReserveRequest) {
  return authedFetch<UploadReserveResponse>("/api/media/upload", {
    method: "POST",
    body: JSON.stringify(req),
  });
}

/** PUT the file bytes directly to S3 / MinIO using the presigned URL. */
export async function putFileToPresignedUrl(presignedUrl: string, file: File): Promise<void> {
  const res = await fetch(presignedUrl, {
    method: "PUT",
    body: file,
    headers: { "Content-Type": file.type },
  });
  if (!res.ok) throw new Error(`S3 PUT failed: HTTP ${res.status}`);
}

export function completeUpload(uploadId: string) {
  return authedFetch<void>(`/api/media/upload/${uploadId}/complete`, { method: "POST" });
}

export function getUploadStatus(uploadId: string) {
  return authedFetch<UploadStatusResponse>(`/api/media/status/${uploadId}`);
}

export function publishMetadata(req: MetadataRequest) {
  return authedFetch<PublishedMedia>("/api/media/metadata", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
