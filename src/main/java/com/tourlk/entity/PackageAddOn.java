package com.tourlk.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An optional extra a guide attaches to a {@link TourPackage}: either a hotel {@link Room} type or a
 * {@link Vehicle} (exactly one of the two is set). Tourists can add these when booking the package.
 * <p>
 * Uniqueness per (package, room) and (package, vehicle) is enforced in {@code PackageAddOnServiceImpl}
 * rather than with DB unique constraints: SQL Server treats NULLs as equal in a unique constraint, so a
 * plain constraint would reject a second vehicle add-on (room = NULL) on the same package.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "package_add_ons")
public class PackageAddOn extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @Column(length = 300)
    private String note;

}
