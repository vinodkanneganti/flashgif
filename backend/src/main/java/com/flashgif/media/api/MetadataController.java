package com.flashgif.media.api;

import com.flashgif.media.api.dto.MetadataRequest;
import com.flashgif.media.api.dto.PublishResponse;
import com.flashgif.media.domain.PublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media Metadata", description = "Publishes a READY upload as a searchable media entity.")
class MetadataController {

    private final PublishService publishService;

    @PostMapping("/metadata")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit title, tags, rating for a READY upload; creates the Media row + triggers indexing.")
    public PublishResponse submit(@Valid @RequestBody MetadataRequest req) {
        return publishService.publish(req);
    }
}
