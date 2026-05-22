package com.flashgif.search.api;

import com.flashgif.media.api.dto.MediaSummary;
import com.flashgif.search.api.dto.SearchResponse;
import com.flashgif.search.api.dto.SuggestionsResponse;
import com.flashgif.search.domain.SearchService;
import com.flashgif.search.domain.SearchSort;
import com.flashgif.search.domain.SuggestionService;
import com.flashgif.search.domain.TrendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Keyword search, trending, and autocomplete.")
class SearchController {

    private final SearchService searchService;
    private final TrendingService trendingService;
    private final SuggestionService suggestionService;

    @GetMapping("/search")
    @Operation(summary = "Keyword search across published media.")
    public SearchResponse search(
            @Parameter(description = "Query terms. Empty falls through to trending.") @RequestParam(required = false) String q,
            @Parameter(description = "Filter by type: gif or sticker.") @RequestParam(required = false) String type,
            @Parameter(description = "Sort mode: relevance (default) or recency.") @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return searchService.search(q, type, SearchSort.parse(sort), page, size);
    }

    @GetMapping("/trending")
    @Operation(summary = "Trending media, sorted by popularity.")
    public List<MediaSummary> trending(
            @Parameter(description = "Filter by type: gif or sticker. Omit for all.") @RequestParam(required = false) String type
    ) {
        return trendingService.top(type);
    }

    @GetMapping("/search/suggestions")
    @Operation(summary = "Autocomplete suggestions for a prefix.")
    public SuggestionsResponse suggestions(@RequestParam String q) {
        return new SuggestionsResponse(suggestionService.suggest(q));
    }
}
