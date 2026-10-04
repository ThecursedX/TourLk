package com.tourlk.enums;

/**
 * Lifecycle status of a destination.
 * <p>
 * DRAFT -&gt; PENDING_REVIEW -&gt; PUBLISHED, with TEMPORARILY_CLOSED
 * (still visible, with a warning), INACTIVE (hidden from dropdowns and
 * browsing) and ARCHIVED (hidden publicly, kept for admins). Replaces the
 * old ACTIVE value (migrated to PUBLISHED by {@code StatusMigrationRunner}).
 */
public enum DestinationStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    TEMPORARILY_CLOSED,
    INACTIVE,
    ARCHIVED
}
