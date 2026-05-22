"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/client";
import {
  completeUpload, getUploadStatus, publishMetadata, putFileToPresignedUrl, reserveUpload,
  type UploadStatusResponse,
} from "@/lib/api/media";
import { fileValidationError, metadataSchema, parseTags, type MetadataValues } from "@/lib/upload/schemas";
import { useMe } from "@/lib/query/authHooks";

type Stage =
  | { kind: "pick" }
  | { kind: "uploading"; file: File }
  | { kind: "processing"; uploadId: string; file: File }
  | { kind: "metadata"; uploadId: string; status: UploadStatusResponse; file: File }
  | { kind: "error"; message: string };

export function UploadModal({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const { data: me } = useMe();
  const [stage, setStage] = useState<Stage>({ kind: "pick" });

  // Esc to close.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  async function startUpload(file: File) {
    const err = fileValidationError(file);
    if (err) { setStage({ kind: "error", message: err }); return; }

    setStage({ kind: "uploading", file });
    try {
      const reserved = await reserveUpload({
        filename: file.name,
        content_type: file.type,
        size: file.size,
      });
      await putFileToPresignedUrl(reserved.presigned_url, file);
      await completeUpload(reserved.upload_id);
      setStage({ kind: "processing", uploadId: reserved.upload_id, file });
    } catch (e) {
      const msg = e instanceof ApiError ? `Upload failed (${e.status})` : "Upload failed";
      setStage({ kind: "error", message: msg });
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
      role="dialog"
      aria-modal="true"
      aria-label="Upload media"
    >
      <div className="w-full max-w-xl rounded-lg bg-background border shadow-xl overflow-hidden flex flex-col max-h-[90vh]">
        <div className="flex items-center justify-between p-4 border-b shrink-0">
          <h2 className="text-lg font-semibold">Upload media</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="p-1 rounded-md hover:bg-accent"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="p-4 overflow-y-auto flex-1">
          {stage.kind === "pick" && <Dropzone onFile={startUpload} />}

          {stage.kind === "uploading" && (
            <BusyState label={`Uploading ${stage.file.name}…`} />
          )}

          {stage.kind === "processing" && (
            <Poller
              uploadId={stage.uploadId}
              onReady={(status) => setStage({ kind: "metadata", uploadId: stage.uploadId, status, file: stage.file })}
              onFailed={(reason) => setStage({ kind: "error", message: reason ?? "Transcode failed" })}
            />
          )}

          {stage.kind === "metadata" && (
            <MetadataForm
              uploadId={stage.uploadId}
              onPublished={() => {
                onClose();
                // Go to the uploader's own channel so the new media is visible.
                router.push(me?.username ? `/channels/${me.username}` : "/");
                router.refresh();
              }}
            />
          )}

          {stage.kind === "error" && (
            <div className="space-y-3">
              <div role="alert" className="text-sm text-destructive">{stage.message}</div>
              <Button onClick={() => setStage({ kind: "pick" })}>Try again</Button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------- Dropzone ----------------

function Dropzone({ onFile }: { onFile: (file: File) => void }) {
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  return (
    <div>
      <div
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          const file = e.dataTransfer.files?.[0];
          if (file) onFile(file);
        }}
        className={[
          "rounded-lg border-2 border-dashed p-12 text-center cursor-pointer",
          "transition-colors",
          dragging ? "border-primary bg-accent" : "border-muted-foreground/30 hover:border-primary/50",
        ].join(" ")}
        role="button"
        tabIndex={0}
        aria-label="Choose or drop a file to upload"
      >
        <p className="text-sm font-medium">Drag &amp; drop a GIF, MP4, WebM, or MOV</p>
        <p className="text-xs text-muted-foreground mt-1">or click to choose</p>
        <p className="text-xs text-muted-foreground mt-3">Max 100 MB</p>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept="video/mp4,video/webm,video/quicktime,image/gif"
        className="sr-only"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onFile(file);
        }}
      />
    </div>
  );
}

// ---------------- Busy state ----------------

function BusyState({ label }: { label: string }) {
  return (
    <div className="py-10 text-center">
      <div className="inline-block h-6 w-6 rounded-full border-2 border-primary border-t-transparent animate-spin" />
      <div className="mt-3 text-sm text-muted-foreground">{label}</div>
    </div>
  );
}

// ---------------- Poller ----------------

function Poller({
  uploadId,
  onReady,
  onFailed,
}: {
  uploadId: string;
  onReady: (status: UploadStatusResponse) => void;
  onFailed: (reason?: string) => void;
}) {
  const [status, setStatus] = useState<UploadStatusResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | null = null;

    async function tick() {
      try {
        const s = await getUploadStatus(uploadId);
        if (cancelled) return;
        setStatus(s);
        if (s.status === "READY" || s.status === "PUBLISHED") {
          onReady(s);
          return;
        }
        if (s.status === "FAILED") {
          onFailed(s.failure_reason ?? undefined);
          return;
        }
        timer = setTimeout(tick, 1500);
      } catch {
        if (!cancelled) timer = setTimeout(tick, 3000);
      }
    }

    tick();
    return () => { cancelled = true; if (timer) clearTimeout(timer); };
  }, [uploadId, onReady, onFailed]);

  const label = status?.status === "PROCESSING"
    ? "Transcoding renditions…"
    : status?.status === "UPLOADED"
    ? "Queueing for transcode…"
    : "Working…";

  return <BusyState label={label} />;
}

// ---------------- Metadata form ----------------

function MetadataForm({
  uploadId,
  onPublished,
}: {
  uploadId: string;
  onPublished: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<MetadataValues>({
    resolver: zodResolver(metadataSchema),
    defaultValues: { type: "gif", content_rating: "g" },
  });

  async function onSubmit(values: MetadataValues) {
    try {
      await publishMetadata({
        upload_id: uploadId,
        title: values.title,
        description: values.description,
        type: values.type,
        content_rating: values.content_rating,
        tags: parseTags(values.tagsRaw),
      });
      onPublished();
    } catch (e) {
      // Surface backend's error body if present — much more actionable than a bare 5xx.
      let msg = "Publish failed";
      if (e instanceof ApiError) {
        const body = e.body as { detail?: { message?: string }; error?: string; message?: string } | undefined;
        msg = body?.detail?.message
            ?? body?.message
            ?? body?.error
            ?? `Publish failed (${e.status})`;
      }
      setError("root", { message: msg });
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-4" aria-label="Media metadata">
      <div className="space-y-1">
        <label htmlFor="title" className="text-sm font-medium">Title</label>
        <Input id="title" {...register("title")} />
        {errors.title && <p className="text-xs text-destructive">{errors.title.message}</p>}
      </div>

      <div className="space-y-1">
        <label htmlFor="description" className="text-sm font-medium">Description (optional)</label>
        <Input id="description" {...register("description")} />
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1">
          <label htmlFor="type" className="text-sm font-medium">Type</label>
          <select
            id="type"
            {...register("type")}
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
          >
            <option value="gif">GIF</option>
            <option value="sticker">Sticker</option>
          </select>
        </div>
        <div className="space-y-1">
          <label htmlFor="content_rating" className="text-sm font-medium">Rating</label>
          <select
            id="content_rating"
            {...register("content_rating")}
            className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
          >
            <option value="g">G</option>
            <option value="pg">PG</option>
            <option value="pg13">PG-13</option>
            <option value="r">R</option>
          </select>
        </div>
      </div>

      <div className="space-y-1">
        <label htmlFor="tagsRaw" className="text-sm font-medium">Tags (comma-separated)</label>
        <Input id="tagsRaw" placeholder="happy, celebration, dance" {...register("tagsRaw")} />
        <p className="text-xs text-muted-foreground">Up to 20, lowercase, no #.</p>
      </div>

      {errors.root && (
        <div role="alert" className="text-sm text-destructive">{errors.root.message}</div>
      )}

      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? "Publishing…" : "Publish"}
      </Button>
    </form>
  );
}
