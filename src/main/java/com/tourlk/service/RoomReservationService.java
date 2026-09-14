package com.tourlk.service;

import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.User;

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

    RoomReservationResponseDto completeReservation(Long id, User currentUser);

    RoomReservationResponseDto getReservationById(Long id, User currentUser);

    List<RoomReservationResponseDto> getReservationsByTourist(Long touristId);

    List<RoomReservationResponseDto> getReservationsByRoom(Long roomId, User currentUser);

}
