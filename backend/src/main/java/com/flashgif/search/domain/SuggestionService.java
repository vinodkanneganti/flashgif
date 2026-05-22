package com.flashgif.search.domain;

import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.flashgif.search.api.dto.Suggestion;
import com.flashgif.search.index.MediaDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private static final int MAX_SUGGESTIONS = 10;

    private final ElasticsearchOperations es;

    /** Cached for 5 minutes per (lowercased) prefix via {@code RedisCacheManager}. */
    @Cacheable(value = "suggestions", key = "#prefix == null ? '' : #prefix.toLowerCase()")
    public List<Suggestion> suggest(String prefix) {
        if (prefix == null || prefix.isBlank()) return List.of();

        // search_as_you_type pattern: query the .suggest field across its built-in n-gram subfields.
        Query q = MultiMatchQuery.of(m -> m
                .query(prefix)
                .type(TextQueryType.BoolPrefix)
                .fields("title.suggest", "title.suggest._2gram", "title.suggest._3gram")
        )._toQuery();

        NativeQuery nq = NativeQuery.builder()
                .withQuery(q)
                .withPageable(PageRequest.of(0, MAX_SUGGESTIONS * 2))
                .build();

        SearchHits<MediaDocument> hits = es.search(nq, MediaDocument.class);

        // Dedupe by title and cap. (Tag-based suggestions can layer in later; titles
        // alone are useful for v1.)
        Set<String> seen = new LinkedHashSet<>();
        for (var h : hits.getSearchHits()) {
            String title = h.getContent().getTitle();
            if (title != null && !title.isBlank()) seen.add(title);
            if (seen.size() >= MAX_SUGGESTIONS) break;
        }
        return seen.stream().map(t -> new Suggestion(t, "title")).toList();
    }
}
