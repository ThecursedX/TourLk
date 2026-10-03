package com.tourlk.service;

import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.Booking;
import com.tourlk.entity.User;

import java.time.LocalDate;
import java.util.List;

public interface RoomReservationService {

    RoomReservationResponseDto createReservation(RoomReservationRequestDto request, User currentUser);

    RoomReservationResponseDto confirmReservation(Long id, User currentUser);

    /**
     * Confirms a reservation on behalf of the platform itself (e.g. after
     * a successful payment webhook), skipping the owner-or-admin check —
     * there is no authenticated actor in that flow. Not exposed via any
     * controller; callable only from other services.
     */
    RoomReservationResponseDto confirmReservationAfterPayment(Long id);

    RoomReservationResponseDto cancelReservation(Long id, User currentUser);

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
