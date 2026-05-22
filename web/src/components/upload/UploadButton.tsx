"use client";

import { useState } from "react";
import { Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { UploadModal } from "./UploadModal";

export function UploadButton() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button
        variant="default"
        size="sm"
        onClick={() => setOpen(true)}
        className="gap-1"
        aria-label="Upload media"
      >
        <Upload className="h-4 w-4" />
        <span className="hidden sm:inline">Upload</span>
      </Button>
      {open && <UploadModal onClose={() => setOpen(false)} />}
    </>
  );
}
