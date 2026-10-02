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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A scheduled departure date of a {@link TourPackage}. Once a package has
 * at least one departure, bookings (and reschedules) may only target one
 * of its departure dates, and {@link #seatsTotal} replaces the package's
 * maxCapacity as that date's seat limit — see {@code BookingServiceImpl}.
 * Packages with no departures keep accepting any future travel date.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "package_departures",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_package_departure_date", columnNames = {"tour_package_id", "departure_date"}))
public class PackageDeparture extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    /** Defaults to the package's maxCapacity when not given on creation. */
    @Column(name = "seats_total", nullable = false)
    private int seatsTotal;

}
