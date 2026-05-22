package com.flashgif.search.index;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * ES projection of {@link com.flashgif.media.domain.Media}. The authoritative
 * mapping lives in {@code resources/elasticsearch/media-mapping.json} and is
 * applied by {@link IndexInitializer} at startup; the annotations here are
 * informational so devs can read the field shape from one place.
 */
@Document(indexName = "media", createIndex = false)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MediaDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(name = "content_rating", type = FieldType.Keyword)
    private String contentRating;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Float)
    private float popularity;

    @Field(name = "view_count", type = FieldType.Long)
    private long viewCount;

    @Field(name = "favorite_count", type = FieldType.Long)
    private long favoriteCount;

    @Field(name = "created_at", type = FieldType.Date)
    private OffsetDateTime createdAt;

    @Field(name = "uploader_username", type = FieldType.Keyword)
    private String uploaderUsername;

    @Field(type = FieldType.Integer)
    private Integer width;

    @Field(type = FieldType.Integer)
    private Integer height;

    @Field(name = "rendition_urls", type = FieldType.Object, enabled = false)
    private Map<String, String> renditionUrls;
}
