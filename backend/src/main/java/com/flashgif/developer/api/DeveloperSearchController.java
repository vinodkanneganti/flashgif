package com.flashgif.developer.api;

import com.flashgif.media.api.dto.MediaSummary;
import com.flashgif.search.api.dto.SearchResponse;
import com.flashgif.search.api.dto.SuggestionsResponse;
import com.flashgif.search.domain.SearchService;
import com.flashgif.search.domain.SearchSort;
import com.flashgif.search.domain.SuggestionService;
import com.flashgif.search.domain.TrendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Third-party developer surface — same business logic as the public search
 * endpoints, but auth'd via API key and rate-limited per key.
 */
@RestController
@RequestMapping("/api/v1/developer")
@RequiredArgsConstructor
@Tag(name = "Developer Search", description = "Third-party-facing search via API key.")
@SecurityRequirement(name = "bearer-jwt")    // same Bearer header shape; Swagger lets you paste either token
class DeveloperSearchController {

    private final SearchService searchService;
    private final TrendingService trendingService;
    private final SuggestionService suggestionService;

    @GetMapping("/search")
    @Operation(summary = "Keyword search (API-key authenticated).")
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return searchService.search(q, type, SearchSort.parse(sort), page, size);
    }

    @GetMapping("/trending")
    @Operation(summary = "Trending media (API-key authenticated).")
    public List<MediaSummary> trending(@RequestParam(required = false) String type) {
        return trendingService.top(type);
    }

    @GetMapping("/search/suggestions")
    @Operation(summary = "Autocomplete suggestions (API-key authenticated).")
    public SuggestionsResponse suggestions(@RequestParam String q) {
        return new SuggestionsResponse(suggestionService.suggest(q));
    }
}
