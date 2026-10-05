package com.tourlk.service;

import com.tourlk.dto.CancellationPreviewResponseDto;
import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.User;
import com.tourlk.enums.RoomReservationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface RoomReservationService {

    RoomReservationResponseDto createReservation(RoomReservationRequestDto request, User currentUser);

    /**
     * Confirms a reservation on behalf of the platform itself, after a
     * successful payment: reservations are never confirmed by hand, so
     * payment is the only way in. There is no authenticated actor in that
     * flow. Not exposed via any controller; callable only from other services.
     */
    RoomReservationResponseDto confirmReservationAfterPayment(Long id);

    /**
     * Cancels a reservation. A SUCCEEDED payment of a standalone reservation is
     * refunded per the {@link RoomCancellationPolicy} when the tourist cancels,
     * and in full when the owner or an admin does.
     */
    RoomReservationResponseDto cancelReservation(Long id, User currentUser);

    /** What cancelling this reservation today would refund for the caller, without cancelling it. */
    CancellationPreviewResponseDto getCancellationPreview(Long id, User currentUser);

    /**
     * Cancels the property's reservations in {@code statuses} (stays that already ended are skipped),
     * refunding any payment in full and notifying each tourist; returns how many were cancelled.
     * Joins the caller's transaction.
     */
    int cancelForAccommodation(Long accommodationId, Set<RoomReservationStatus> statuses, String reason);

    /** Creates a PENDING reservation that belongs to {@code booking}; the booking pays for it. */
    RoomReservationResponseDto createLinkedReservation(Booking booking, Long roomId, int numberOfRooms,
                                                       LocalDate checkIn, LocalDate checkOut);

    /** Cancels every non-terminal reservation of the booking (booking cancelled/rejected/closed). */
    void cancelLinkedToBooking(Long bookingId);

    /** Confirms every still-PENDING reservation of the booking after the booking was paid. */
    void confirmLinkedAfterPayment(Long bookingId);

    /** Moves the booking's live reservations to new dates (booking rescheduled); re-checks availability. */
    void moveLinkedToDates(Long bookingId, LocalDate checkIn, LocalDate checkOut);

    List<RoomReservationResponseDto> getLinkedToBooking(Long bookingId);

    /** Cancels a still-PENDING reservation whose payment window lapsed; returns whether it was cancelled. */
    boolean expireUnpaidReservation(Long id);

    RoomReservationResponseDto completeReservation(Long id, User currentUser);

    RoomReservationResponseDto getReservationById(Long id, User currentUser);

    List<RoomReservationResponseDto> getReservationsByTourist(Long touristId);

    List<RoomReservationResponseDto> getReservationsByRoom(Long roomId, User currentUser);

}
