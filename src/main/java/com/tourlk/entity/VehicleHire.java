package com.tourlk.entity;

import com.tourlk.enums.VehicleHireStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A tourist's hire of a {@link Vehicle} for a date range. Goes through
 * PENDING -&gt; CONFIRMED -&gt; COMPLETED (or CANCELLED) managed by
 * {@code VehicleHireServiceImpl}. Unlike a Room, a Vehicle is a single
 * unit — any overlapping CONFIRMED hire blocks a new one outright, no
 * partial capacity.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vehicle_hires")
public class VehicleHire extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tourist_id", nullable = false)
    private User tourist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "pickup_location", nullable = false, length = 200)
    private String pickupLocation;

    @Column(length = 1000)
    private String notes;

    /** Computed as vehicle.pricePerDay * number of days, at creation time. */
    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VehicleHireStatus status = VehicleHireStatus.PENDING;

}
