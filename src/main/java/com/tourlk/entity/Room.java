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
 * A room type within an {@link Accommodation}, e.g. "Deluxe Double".
 * {@code totalRooms} is how many physical units of this type the
 * property has — the ceiling that {@code RoomReservationServiceImpl}
 * checks overlapping reservations against.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "rooms")
public class Room extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accommodation_id", nullable = false)
    private Accommodation accommodation;

    @Column(name = "room_type", nullable = false, length = 150)
    private String roomType;

    @Column(name = "price_per_night", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_facilities", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "facility", length = 100)
    @Builder.Default
    private List<String> facilities = new ArrayList<>();

    /** Image URLs in display order (max 10); the first one is the cover image. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "room_images", joinColumns = @JoinColumn(name = "room_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", length = 1000)
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

}
