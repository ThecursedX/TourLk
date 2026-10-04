package com.tourlk.enums;

import com.tourlk.exception.BadRequestException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Sort orders for browsing tour packages, addressed by their lowercase
 * query-string value (e.g. {@code ?sort=price_asc}).
 */
public enum PackageSort {
    /** Cheapest first. */
    PRICE_ASC,
    /** Most expensive first. */
    PRICE_DESC,
    /** Shortest first. */
    DURATION,
    /** Highest average rating first; more reviews wins a tie. */
    RATING,
    /** Most recently created first. */
    NEWEST;

    /** Null/blank means "no particular order"; anything unrecognised is a 400. */
    public static PackageSort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown sort '" + value + "'. Use one of: " + Arrays.stream(values())
                    .map(sort -> sort.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(", ")));
        }
    }
}
