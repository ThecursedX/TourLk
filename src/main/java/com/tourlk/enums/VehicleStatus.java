package com.tourlk.enums;

/**
 * Lifecycle status of a vehicle listing.
 * <p>
 * DRAFT -&gt; PENDING_VERIFICATION -&gt; AVAILABLE, with BOOKED,
 * UNDER_MAINTENANCE and OUT_OF_SERVICE as operational states and
 * ARCHIVED as the soft-delete state. Replaces the old
 * PENDING_APPROVAL / ACTIVE / INACTIVE values (migrated by
 * {@code StatusMigrationRunner}).
 */
public enum VehicleStatus {
    DRAFT,
    PENDING_VERIFICATION,
    AVAILABLE,
    BOOKED,
    UNDER_MAINTENANCE,
    OUT_OF_SERVICE,
    ARCHIVED
}
