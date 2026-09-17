package com.tourlk.entity;

import com.tourlk.enums.DestinationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    /** e.g. "Southern Province", "Central Highlands". */
    @Column(nullable = false, length = 150)
    private String region;

    @Lob
    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DestinationStatus status = DestinationStatus.ACTIVE;

}
