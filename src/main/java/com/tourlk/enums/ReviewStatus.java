package com.tourlk.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Moderation/lifecycle status of a {@code Review}. Replaces the old
 * {@code flagged} boolean: PUBLISHED/EDITED are the two "normal" states
 * (EDITED just records that the reviewer has changed it at least once),
 * REPORTED covers both auto-flagged-for-profanity and admin-reported
 * content, and HIDDEN/DELETED are admin moderation actions that keep the
 * row (for audit / the reviewer's own history) but pull it out of public
 * listings and rating averages.
 */
public enum ReviewStatus {
    PUBLISHED,
    EDITED,
    REPORTED,
    HIDDEN,
    DELETED;

    /** Statuses excluded from public review lists and rating-average calculations. */
    public static final Set<ReviewStatus> HIDDEN_FROM_PUBLIC = EnumSet.of(HIDDEN, DELETED);
}
