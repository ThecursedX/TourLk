package com.tourlk.enums;

import java.util.List;
import java.util.Locale;

/**
 * The nine provinces of Sri Lanka, each with its districts. The enum
 * constant names are what travel over the API (e.g. {@code "NORTH_WESTERN"})
 * and must stay in sync with the {@code Province} type in the frontend's
 * {@code types/destination.ts}.
 * <p>
 * {@link #getDisplayName()} ("Southern Province") is also what gets written
 * to the legacy {@code destinations.region} column so older rows/queries
 * keep working.
 */
public enum Province {

    WESTERN("Western Province", List.of("Colombo", "Gampaha", "Kalutara")),
    CENTRAL("Central Province", List.of("Kandy", "Matale", "Nuwara Eliya")),
    SOUTHERN("Southern Province", List.of("Galle", "Matara", "Hambantota")),
    NORTHERN("Northern Province", List.of("Jaffna", "Kilinochchi", "Mannar", "Mullaitivu", "Vavuniya")),
    EASTERN("Eastern Province", List.of("Batticaloa", "Ampara", "Trincomalee")),
    NORTH_WESTERN("North Western Province", List.of("Kurunegala", "Puttalam")),
    NORTH_CENTRAL("North Central Province", List.of("Anuradhapura", "Polonnaruwa")),
    UVA("Uva Province", List.of("Badulla", "Monaragala")),
    SABARAGAMUWA("Sabaragamuwa Province", List.of("Ratnapura", "Kegalle"));

    private final String displayName;
    private final List<String> districts;

    Province(String displayName, List<String> districts) {
        this.displayName = displayName;
        this.districts = districts;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDistricts() {
        return districts;
    }

    /**
     * Returns this province's canonical spelling of {@code district}
     * (case-insensitive match), or {@code null} if the district does not
     * belong to this province.
     */
    public String canonicalDistrict(String district) {
        if (district == null) {
            return null;
        }
        String wanted = district.trim();
        for (String candidate : districts) {
            if (candidate.equalsIgnoreCase(wanted)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Best-effort mapping of a legacy free-text region such as
     * "Southern Province" or "north western" to a Province. Returns
     * {@code null} when it doesn't match one of the nine (e.g. "Central
     * Highlands"), so callers must handle that.
     */
    public static Province fromLabel(String label) {
        if (label == null) {
            return null;
        }
        String normalised = normalise(label);
        if (normalised.endsWith("province")) {
            normalised = normalised.substring(0, normalised.length() - "province".length());
        }
        for (Province province : values()) {
            if (normalise(province.name()).equals(normalised)) {
                return province;
            }
        }
        return null;
    }

    private static String normalise(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

}
