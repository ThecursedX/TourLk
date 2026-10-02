package com.tourlk.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
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
 * One day of a {@link TourPackage}'s itinerary. The whole set of a
 * package's days is replaced wholesale on every create/update (deleted
 * then re-inserted) by {@code TourPackageServiceImpl} rather than
 * diffed — same "clear and re-add" approach as {@code Destination#imageUrls}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "itinerary_days")
public class ItineraryDay extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    /** 1-based; unique within its package and at most the package's durationDays — enforced in the service. */
    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "itinerary_day_places", joinColumns = @JoinColumn(name = "itinerary_day_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "place", length = 200)
    @Builder.Default
    private List<String> placesToVisit = new ArrayList<>();

}
