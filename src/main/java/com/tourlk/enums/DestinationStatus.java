package com.tourlk.enums;

/**
 * Lifecycle status of a destination.
 * <p>
 * Simpler than the DRAFT -&gt; PENDING_APPROVAL -&gt; ACTIVE lifecycle used
 * by Tour Package / Accommodation: destinations are curated directly by
 * ADMINs, so there is no submission/approval step — a destination is
 * either ACTIVE (selectable and browsable) or INACTIVE (hidden from
 * dropdowns and public browsing, but still referenced by any existing
 * packages/accommodations that were created while it was active).
 */
public enum DestinationStatus {
    ACTIVE,
    INACTIVE
}
