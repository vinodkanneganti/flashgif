package com.flashgif.search.index;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Simple CRUD-by-id ops for {@link MediaDocument}. Complex queries
 * (multi_match, function_score, search_as_you_type) go through
 * {@code ElasticsearchOperations} in the service layer instead.
 */
public interface MediaSearchRepository extends ElasticsearchRepository<MediaDocument, String> {
}
