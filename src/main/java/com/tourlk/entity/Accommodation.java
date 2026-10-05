package com.tourlk.entity;

import com.tourlk.enums.AccommodationStatus;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A hotel/property listed by a HOTEL_PARTNER (or ADMIN), containing one
 * or more {@link Room} types. Goes through the same
 * DRAFT -&gt; PENDING_APPROVAL -&gt; ACTIVE / INACTIVE lifecycle as
 * {@code TourPackage}, managed by {@code AccommodationServiceImpl}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "accommodations")
public class Accommodation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Lob
    @Column(nullable = false)
    private String description;

    /**
     * The place this property is in. Migrated from a free-text String to
     * a real {@link Destination} relation (same migration as
     * {@code TourPackage.destination}) so browsing/filtering by location
     * is consistent across the platform. Field kept named {@code location}
     * to minimise churn; required.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Destination location;

    /** 1-5, nullable — not every listing has a rating yet. */
    @Column(name = "star_rating")
    private Integer starRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AccommodationStatus status = AccommodationStatus.DRAFT;

    @Column(length = 500)
    private String address;

    /** Map position, both set or both null. Optional; existing rows stay null. */
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "accommodation_facilities", joinColumns = @JoinColumn(name = "accommodation_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "facility", length = 100)
    @Builder.Default
    private List<String> facilities = new ArrayList<>();

    /** House rules / cancellation and check-in policies, free text. */
    @Lob
    private String policies;

    /** Image URLs in display order (max 10); the first one is the cover image. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "accommodation_images", joinColumns = @JoinColumn(name = "accommodation_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /** True while an ADMIN's deactivation stands: the owner may not reactivate it. Null/false otherwise. */
    @Column(name = "deactivated_by_admin")
    private Boolean deactivatedByAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

}
