package com.tourlk.repo;

import com.tourlk.entity.Booking;
import com.tourlk.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByTouristId(Long touristId);

    List<Booking> findByTourPackageId(Long packageId);

    /**
     * Sums travelers across bookings that currently occupy capacity for a
     * given package + date (CONFIRMED and RESCHEDULED — see
     * {@link BookingStatus} for why RESCHEDULED counts too).
     * <p>
     * Deliberately NOT annotated with {@code @Lock}: locking an aggregate
     * SELECT only locks rows it matches, and when zero bookings exist yet
     * for a date there's nothing to lock, so two concurrent transactions
     * could both read 0 and both proceed — the classic phantom-read gap.
     * Safe to run unlocked here because the caller
     * ({@code BookingServiceImpl}) always holds a pessimistic write lock
     * on the parent {@code TourPackage} row first, which serializes every
     * capacity check/booking for that package into one at a time.
     */
    @Query("SELECT COALESCE(SUM(b.numberOfTravelers), 0) FROM Booking b "
            + "WHERE b.tourPackage.id = :packageId AND b.travelDate = :travelDate AND b.status IN :statuses")
    int sumTravelersByPackageAndDateAndStatusIn(@Param("packageId") Long packageId,
                                                 @Param("travelDate") LocalDate travelDate,
                                                 @Param("statuses") Collection<BookingStatus> statuses);

}
