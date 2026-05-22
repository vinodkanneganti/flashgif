package com.flashgif.search.domain;

import com.flashgif.media.api.dto.MediaSummary;
import com.flashgif.search.index.MediaDocument;
import org.springframework.stereotype.Component;

@Component
class MediaProjector {

    MediaSummary toSummary(MediaDocument d) {
        return new MediaSummary(
                d.getId(),
                d.getTitle(),
                d.getTags(),
                d.getType(),
                d.getContentRating(),
                d.getViewCount(),
                d.getFavoriteCount(),
                d.getWidth(),
                d.getHeight(),
                d.getRenditionUrls(),
                d.getCreatedAt()
        );
    }
}
