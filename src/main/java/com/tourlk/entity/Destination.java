package com.tourlk.entity;

import com.tourlk.enums.DestinationStatus;
import com.tourlk.enums.Province;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A real place (city/region/landmark) tourists can browse, e.g. "Ella",
 * "Sigiriya", "Galle Fort". Curated directly by ADMINs via
 * {@code DestinationServiceImpl} — no submission/approval workflow.
 * <p>
 * {@link TourPackage} and {@link Accommodation} reference a Destination
 * instead of a free-text string so browsing/filtering by destination is
 * consistent and typo-free across the platform.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "destinations")
public class Destination extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    /**
     * Legacy free-text region ("Southern Province"), superseded by
     * {@link #province}. Nullable because some databases have no such column
     * (Hibernate then adds it as NULL-able, which SQL Server allows on a table
     * that already has rows; a NOT NULL column it would refuse to add).
     * Still filled from the province's display name on every save.
     */
    @Column(length = 150)
    private String region;

    /**
     * Nullable at the database level so {@code ddl-auto: update} can add the
     * column to a table that already has rows. Rows created before this
     * field existed fall back to {@link #resolveProvince()}.
     */
    @Convert(converter = ProvinceConverter.class)
    @Column(length = 30)
    private Province province;

    @Column(length = 100)
    private String district;

    /** Open-ended label such as "Beach", "Wildlife" or "Cultural". */
    @Column(length = 100)
    private String category;

    @Column(name = "best_time_to_visit", length = 200)
    private String bestTimeToVisit;

    /** Image URLs in display order; the first one is the cover image. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "destination_images", joinColumns = @JoinColumn(name = "destination_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Lob
    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DestinationStatus status = DestinationStatus.ACTIVE;

    /**
     * The province to show for this destination: the stored one, or a
     * best-effort match on the legacy region text for older rows. May be
     * {@code null} if neither yields one.
     */
    public Province resolveProvince() {
        return province != null ? province : Province.fromLabel(region);
    }

    /** The stored region text, or the province's display name when the column is empty. */
    public String resolveRegion() {
        if (region != null) {
            return region;
        }
        Province resolved = resolveProvince();
        return resolved != null ? resolved.getDisplayName() : null;
    }

}
