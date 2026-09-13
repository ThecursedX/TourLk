package com.tourlk.controller;

import com.tourlk.dto.RoomReservationRequestDto;
import com.tourlk.dto.RoomReservationResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.RoomReservationService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Room reservation creation, confirmation and cancellation workflow.
 * Mirrors the Booking module's conventions.
 */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Room Reservations", description = "Reserve and manage accommodation room bookings")
public class RoomReservationController {

    private final RoomReservationService roomReservationService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<RoomReservationResponseDto> create(@Valid @RequestBody RoomReservationRequestDto request,
                                                               Authentication authentication) {
        RoomReservationResponseDto response =
                roomReservationService.createReservation(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<RoomReservationResponseDto> confirm(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(roomReservationService.confirmReservation(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('TOURIST','ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<RoomReservationResponseDto> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(roomReservationService.cancelReservation(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<RoomReservationResponseDto> complete(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(roomReservationService.completeReservation(id, currentUser(authentication)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<List<RoomReservationResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(roomReservationService.getReservationsByTourist(currentUser.getId()));
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<List<RoomReservationResponseDto>> byRoom(@PathVariable Long roomId,
                                                                     Authentication authentication) {
        return ResponseEntity.ok(
                roomReservationService.getReservationsByRoom(roomId, currentUser(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomReservationResponseDto> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(roomReservationService.getReservationById(id, currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
