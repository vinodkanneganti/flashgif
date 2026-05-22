package com.flashgif.media.domain;

public enum UploadStatus {
    AWAITING_UPLOAD,
    UPLOADED,
    PROCESSING,
    READY,
    FAILED,
    PUBLISHED;

    public boolean canTransitionTo(UploadStatus next) {
        return switch (this) {
            case AWAITING_UPLOAD -> next == UPLOADED   || next == FAILED;
            case UPLOADED        -> next == PROCESSING || next == FAILED;
            case PROCESSING      -> next == READY      || next == FAILED;
            case READY           -> next == PUBLISHED  || next == FAILED;
            case FAILED, PUBLISHED -> false;
        };
    }
}
