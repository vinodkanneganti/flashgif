package com.flashgif.favorites.domain;

import com.flashgif.media.domain.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CollectionsService {

    private final MediaCollectionRepository collectionRepository;
    private final CollectionItemRepository itemRepository;
    private final MediaRepository mediaRepository;

    // ---------------------------------------------------------------------
    // CRUD
    // ---------------------------------------------------------------------

    @Transactional
    public MediaCollection create(UUID ownerId, String name, String description, boolean isPublic) {
        MediaCollection c = new MediaCollection();
        c.setOwnerId(ownerId);
        c.setName(name);
        c.setDescription(description);
        c.setPublic(isPublic);
        return collectionRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<MediaCollection> listOwn(UUID ownerId) {
        return collectionRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public List<MediaCollection> listPublicFor(UUID ownerId) {
        return collectionRepository.findByOwnerIdAndIsPublicTrueOrderByCreatedAtDesc(ownerId);
    }

    /** Visible to the owner, or to anyone if {@code is_public}. 404 otherwise (don't leak existence). */
    @Transactional(readOnly = true)
    public MediaCollection requireVisible(UUID collectionId, UUID viewerId) {
        MediaCollection c = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Collection not found"));
        if (!c.isPublic() && !c.getOwnerId().equals(viewerId)) {
            throw new ResponseStatusException(NOT_FOUND, "Collection not found");
        }
        return c;
    }

    @Transactional
    public MediaCollection update(UUID collectionId, UUID ownerId,
                                  String newName, String newDescription, Boolean isPublic) {
        MediaCollection c = requireOwned(collectionId, ownerId);
        if (newName != null)        c.setName(newName);
        if (newDescription != null) c.setDescription(newDescription);
        if (isPublic != null)       c.setPublic(isPublic);
        return collectionRepository.save(c);
    }

    @Transactional
    public void delete(UUID collectionId, UUID ownerId) {
        MediaCollection c = requireOwned(collectionId, ownerId);
        collectionRepository.delete(c);
    }

    // ---------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------

    @Transactional
    public void addItem(UUID collectionId, UUID ownerId, UUID mediaId) {
        requireOwned(collectionId, ownerId);
        if (!mediaRepository.existsById(mediaId)) {
            throw new ResponseStatusException(NOT_FOUND, "Media not found");
        }
        if (itemRepository.existsByCollectionIdAndMediaId(collectionId, mediaId)) {
            return;     // idempotent
        }
        itemRepository.save(CollectionItem.of(collectionId, mediaId));
    }

    @Transactional
    public void removeItem(UUID collectionId, UUID ownerId, UUID mediaId) {
        requireOwned(collectionId, ownerId);
        itemRepository.deleteByCollectionIdAndMediaId(collectionId, mediaId);
    }

    @Transactional(readOnly = true)
    public Page<CollectionItem> listItems(UUID collectionId, UUID viewerId, Pageable pageable) {
        requireVisible(collectionId, viewerId);
        return itemRepository.findByCollectionIdOrderByAddedAtDesc(collectionId, pageable);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private MediaCollection requireOwned(UUID collectionId, UUID ownerId) {
        MediaCollection c = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Collection not found"));
        if (!c.getOwnerId().equals(ownerId)) {
            // Owner mismatch → 404, not 403 (don't leak existence of others' collections)
            throw new ResponseStatusException(NOT_FOUND, "Collection not found");
        }
        return c;
    }
}
