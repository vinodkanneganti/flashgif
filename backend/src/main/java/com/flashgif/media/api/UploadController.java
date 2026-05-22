package com.flashgif.media.api;

import com.flashgif.media.api.dto.UploadRequest;
import com.flashgif.media.api.dto.UploadResponse;
import com.flashgif.media.api.dto.UploadStatusResponse;
import com.flashgif.media.domain.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media Upload", description = "Direct-to-S3 upload orchestration and transcode status.")
class UploadController {

    private final UploadService uploadService;

    @PostMapping("/upload")
    @Operation(summary = "Create an upload slot and return a presigned PUT URL.")
    public UploadResponse create(@Valid @RequestBody UploadRequest req) {
        return uploadService.create(req);
    }

    @PostMapping("/upload/{uploadId}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Signal that the browser finished PUTting the file. Triggers transcode.")
    public void complete(@PathVariable UUID uploadId) {
        uploadService.markUploaded(uploadId);
    }

    @GetMapping("/status/{uploadId}")
    @Operation(summary = "Poll for transcode status and rendition URLs.")
    public UploadStatusResponse status(@PathVariable UUID uploadId) {
        return uploadService.status(uploadId);
    }
}
