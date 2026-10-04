package com.tourlk.entity;

import com.tourlk.enums.VehicleStatus;
import com.tourlk.enums.VehicleType;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A vehicle registered by a DRIVER (or ADMIN) for tourist hire —
 * airport transfers, day trips, multi-day tour transport. A separate
 * bookable thing from TourPackage/Accommodation, with its own
 * availability tracked in {@link VehicleHire}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vehicles")
public class Vehicle extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(nullable = false, length = 100)
    private String make;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "registration_number", nullable = false, unique = true, length = 30)
    private String registrationNumber;

    @Column(name = "seating_capacity", nullable = false)
    private int seatingCapacity;

    @Column(name = "price_per_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private VehicleStatus status = VehicleStatus.PENDING_VERIFICATION;

    /** Nullable (not primitive) so the column can be added to a populated table; null reads as false. */
    @Column(name = "air_conditioned")
    private Boolean airConditioned;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vehicle_facilities", joinColumns = @JoinColumn(name = "vehicle_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "facility", length = 100)
    @Builder.Default
    private List<String> facilities = new ArrayList<>();

    /** Image URLs in display order (max 10); the first one is the cover image. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vehicle_images", joinColumns = @JoinColumn(name = "vehicle_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /** Contact for whoever drives it; falls back to the owner's name/phone when blank. */
    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "driver_phone", length = 30)
    private String driverPhone;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "last_maintenance_date")
    private LocalDate lastMaintenanceDate;

    @Column(name = "next_maintenance_date")
    private LocalDate nextMaintenanceDate;

}
