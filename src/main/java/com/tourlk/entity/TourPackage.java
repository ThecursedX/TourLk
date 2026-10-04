package com.tourlk.entity;

import com.tourlk.enums.PackageStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A bookable tour package, owned by the ADMIN or GUIDE who created it.
 * Goes through the DRAFT -&gt; PENDING_APPROVAL -&gt; ACTIVE / INACTIVE
 * lifecycle managed by {@code TourPackageServiceImpl}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tour_packages")
public class TourPackage extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String description;

    /**
     * The place this package visits. Migrated from a free-text String to
     * a real {@link Destination} relation so browsing/filtering by
     * destination is consistent across the platform. Required.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PackageStatus status = PackageStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    /** Admin's reason from the last rejection; shown to the owner, cleared on resubmit. */
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    /** What's included in the price, e.g. "Airport transfers", "All meals". Optional. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tour_package_inclusions", joinColumns = @JoinColumn(name = "tour_package_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "inclusion", length = 300)
    @Builder.Default
    private List<String> inclusions = new ArrayList<>();

    /** What's explicitly not included, e.g. "International flights". Optional. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tour_package_exclusions", joinColumns = @JoinColumn(name = "tour_package_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "exclusion", length = 300)
    @Builder.Default
    private List<String> exclusions = new ArrayList<>();

    /** Image URLs in display order; the first one is the cover image. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tour_package_images", joinColumns = @JoinColumn(name = "tour_package_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

}
