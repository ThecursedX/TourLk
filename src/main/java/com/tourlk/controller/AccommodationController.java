package com.tourlk.controller;

import com.tourlk.dto.AccommodationRequestDto;
import com.tourlk.dto.AccommodationResponseDto;
import com.tourlk.dto.RoomRequestDto;
import com.tourlk.dto.RoomResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.AccommodationService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Accommodation (property) creation, approval workflow, browsing, and
 * room-type management. Mirrors the Tour Package module's conventions.
 */
@RestController
@RequestMapping("/api/accommodations")
@RequiredArgsConstructor
@Tag(name = "Accommodations", description = "Create, manage and browse hotel/property listings")
public class AccommodationController {

    private final AccommodationService accommodationService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<AccommodationResponseDto> create(@Valid @RequestBody AccommodationRequestDto request,
                                                             Authentication authentication) {
        AccommodationResponseDto response =
                accommodationService.createAccommodation(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<AccommodationResponseDto> update(@PathVariable Long id,
                                                             @Valid @RequestBody AccommodationRequestDto request,
                                                             Authentication authentication) {
        return ResponseEntity.ok(
                accommodationService.updateAccommodation(id, request, currentUser(authentication)));
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<AccommodationResponseDto> submit(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(accommodationService.submitForApproval(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccommodationResponseDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(accommodationService.approveAccommodation(id));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccommodationResponseDto> reject(@PathVariable Long id) {
        return ResponseEntity.ok(accommodationService.rejectAccommodation(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<AccommodationResponseDto> deactivate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(accommodationService.deactivateAccommodation(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<AccommodationResponseDto> reactivate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(accommodationService.reactivateAccommodation(id, currentUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<AccommodationResponseDto> archive(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(accommodationService.archiveAccommodation(id, currentUser(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<AccommodationResponseDto>> browse(
            @RequestParam(required = false) Long locationId) {
        return ResponseEntity.ok(accommodationService.getAllActive(locationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccommodationResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(accommodationService.getById(id));
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccommodationResponseDto>> pendingApproval() {
        return ResponseEntity.ok(accommodationService.getPendingApproval());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<List<AccommodationResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(accommodationService.getByOwner(currentUser.getId()));
    }

    @PostMapping("/{id}/rooms")
    @PreAuthorize("hasAnyRole('ADMIN','HOTEL_PARTNER')")
    public ResponseEntity<RoomResponseDto> addRoom(@PathVariable Long id,
                                                     @Valid @RequestBody RoomRequestDto request,
                                                     Authentication authentication) {
        RoomResponseDto response = accommodationService.addRoom(id, request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
