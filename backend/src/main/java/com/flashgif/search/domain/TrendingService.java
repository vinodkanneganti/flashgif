package com.flashgif.search.domain;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.flashgif.media.api.dto.MediaSummary;
import com.flashgif.search.api.dto.SearchResponse;
import com.flashgif.search.index.MediaDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TrendingService {

    private static final int TRENDING_SIZE = 50;

    private final ElasticsearchOperations es;
    private final MediaProjector projector;

    /** Cached for 60s via {@code RedisCacheManager} config (see CacheConfig). */
    @Cacheable(value = "trending", key = "T(java.util.Objects).toString(#type, 'all')")
    public List<MediaSummary> top(String type) {
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(TermQuery.of(t -> t.field("status").value("published"))._toQuery());
        if (type != null && !type.isBlank()) {
            bool.filter(TermQuery.of(t -> t.field("type").value(type.toLowerCase()))._toQuery());
        }

        Query query = bool.build()._toQuery();

        NativeQuery nq = NativeQuery.builder()
                .withQuery(query)
                .withSort(s -> s.field(f -> f.field("popularity").order(SortOrder.Desc)))
                .withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, TRENDING_SIZE))
                .build();

        SearchHits<MediaDocument> hits = es.search(nq, MediaDocument.class);
        return hits.getSearchHits().stream()
                .map(h -> projector.toSummary(h.getContent()))
                .toList();
    }

    /** Used by {@link SearchService} when the search query is empty. */
    SearchResponse asSearchResponse(String type, int page, int size) {
        List<MediaSummary> all = top(type);
        int from = Math.min(page * size, all.size());
        int to   = Math.min(from + size, all.size());
        return new SearchResponse(all.subList(from, to), page, size, all.size(), 0L);
    }
}
