package com.tourlk.controller;

import com.tourlk.dto.RoomRequestDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.AccommodationService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Direct room-type mutations, split from {@link AccommodationController}
 * because these live at a flat /api/rooms/{id} path rather than nested
 * under their accommodation (only creation is nested, at
 * POST /api/accommodations/{id}/rooms).
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Update or remove a room type")
public class RoomController {

    private final AccommodationService accommodationService;
    private final UserService userService;

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<RoomResponseDto> update(@PathVariable Long id,
                                                    @Valid @RequestBody RoomRequestDto request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(accommodationService.updateRoom(id, request, currentUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<Void> remove(@PathVariable Long id, Authentication authentication) {
        accommodationService.removeRoom(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
