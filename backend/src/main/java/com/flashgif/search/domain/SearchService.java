package com.flashgif.search.domain;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import com.flashgif.media.api.dto.MediaSummary;
import com.flashgif.search.api.dto.SearchResponse;
import com.flashgif.search.index.MediaDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_PAGE = 50;
    private static final int MAX_SIZE = 50;

    private final ElasticsearchOperations es;
    private final TrendingService trendingService;
    private final MediaProjector projector;

    public SearchResponse search(String q, String type, SearchSort sort, int page, int size) {
        int safePage = Math.max(0, Math.min(page, MAX_PAGE));
        int safeSize = Math.max(1, Math.min(size, MAX_SIZE));

        if (q == null || q.isBlank()) {
            // Empty query → trending. Echo the requested paging so the client can keep its UX shape.
            return trendingService.asSearchResponse(type, safePage, safeSize);
        }

        Query keyword = MultiMatchQuery.of(m -> m
                .query(q)
                .fields("title^3", "tags.text^2", "description")
                .fuzziness("AUTO")
                .operator(Operator.And)
        )._toQuery();

        BoolQuery.Builder bool = new BoolQuery.Builder()
                .must(keyword)
                .filter(TermQuery.of(t -> t.field("status").value("published"))._toQuery());

        if (type != null && !type.isBlank()) {
            bool.filter(TermQuery.of(t -> t.field("type").value(type.toLowerCase()))._toQuery());
        }

        Query base = bool.build()._toQuery();

        Query finalQuery = (sort == SearchSort.RECENCY) ? base : wrapWithPopularityBoost(base);

        NativeQueryBuilder nq = NativeQuery.builder()
                .withQuery(finalQuery)
                .withPageable(org.springframework.data.domain.PageRequest.of(safePage, safeSize));

        if (sort == SearchSort.RECENCY) {
            nq.withSort(s -> s.field(f -> f.field("created_at").order(SortOrder.Desc)));
        }

        long t0 = System.currentTimeMillis();
        SearchHits<MediaDocument> hits = es.search(nq.build(), MediaDocument.class);
        long took = System.currentTimeMillis() - t0;

        List<MediaSummary> items = hits.getSearchHits().stream()
                .map(h -> projector.toSummary(h.getContent()))
                .toList();

        return new SearchResponse(items, safePage, safeSize, hits.getTotalHits(), took);
    }

    private Query wrapWithPopularityBoost(Query base) {
        return FunctionScoreQuery.of(fs -> fs
                .query(base)
                .functions(FunctionScore.of(f -> f
                        .fieldValueFactor(fv -> fv
                                .field("popularity")
                                .modifier(FieldValueFactorModifier.Log1p)
                                .factor(0.5)
                                .missing(0.0))))
                .boostMode(FunctionBoostMode.Sum)
        )._toQuery();
    }
}
