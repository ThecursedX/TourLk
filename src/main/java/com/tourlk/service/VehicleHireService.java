package com.tourlk.service;

import com.tourlk.dto.BookedDateRangeDto;
import com.tourlk.dto.VehicleHireRequestDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.User;

import java.time.LocalDate;
import java.util.List;

public interface VehicleHireService {

    VehicleHireResponseDto createHire(VehicleHireRequestDto request, User currentUser);

    VehicleHireResponseDto confirmHire(Long id, User currentUser);

    /**
     * Confirms a hire on behalf of the platform itself (e.g. after a
     * successful payment webhook), skipping the owner-or-admin check —
     * there is no authenticated actor in that flow. Mirrors
     * {@code RoomReservationService#confirmReservationAfterPayment}. Not
     * exposed via any controller; callable only from other services.
     */
    VehicleHireResponseDto confirmHireAfterPayment(Long id);

    VehicleHireResponseDto cancelHire(Long id, User currentUser);

    VehicleHireResponseDto completeHire(Long id, User currentUser);

    VehicleHireResponseDto getHireById(Long id, User currentUser);

    List<VehicleHireResponseDto> getHiresByTourist(Long touristId);

    /** PENDING and CONFIRMED date ranges of a vehicle that end today or later. */
    List<BookedDateRangeDto> getBookedDates(Long vehicleId);

    /** Creates a PENDING hire that belongs to {@code booking}; the booking pays for it. */
    VehicleHireResponseDto createLinkedHire(Booking booking, Long vehicleId, LocalDate startDate, LocalDate endDate,
                                            String pickupLocation);

    /** Cancels every non-terminal hire of the booking (booking cancelled/rejected/closed). */
    void cancelLinkedToBooking(Long bookingId);

    /** Confirms every still-PENDING hire of the booking after the booking was paid. */
    void confirmLinkedAfterPayment(Long bookingId);

    /** Moves the booking's live hires to new dates (booking rescheduled); re-checks availability. */
    void moveLinkedToDates(Long bookingId, LocalDate startDate, LocalDate endDate);

    List<VehicleHireResponseDto> getLinkedToBooking(Long bookingId);

    /** Cancels a still-PENDING hire whose payment window lapsed; returns whether it was cancelled. */
    boolean expireUnpaidHire(Long id);

    List<VehicleHireResponseDto> getHiresByVehicle(Long vehicleId, User currentUser);

}
