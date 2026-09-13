package com.tourlk.controller;

import com.tourlk.dto.VehicleHireRequestDto;
import com.tourlk.dto.VehicleHireResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.UserService;
import com.tourlk.service.VehicleHireService;
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
 * Vehicle hire creation, confirmation and cancellation workflow.
 * Mirrors the Booking / Room Reservation modules' conventions.
 */
@RestController
@RequestMapping("/api/hires")
@RequiredArgsConstructor
@Tag(name = "Vehicle Hires", description = "Hire and manage vehicle bookings")
public class VehicleHireController {

    private final VehicleHireService vehicleHireService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<VehicleHireResponseDto> create(@Valid @RequestBody VehicleHireRequestDto request,
                                                         Authentication authentication) {
        VehicleHireResponseDto response = vehicleHireService.createHire(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<VehicleHireResponseDto> confirm(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(vehicleHireService.confirmHire(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('TOURIST','ADMIN','DRIVER')")
    public ResponseEntity<VehicleHireResponseDto> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(vehicleHireService.cancelHire(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<VehicleHireResponseDto> complete(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(vehicleHireService.completeHire(id, currentUser(authentication)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<List<VehicleHireResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(vehicleHireService.getHiresByTourist(currentUser.getId()));
    }

    @GetMapping("/vehicle/{vehicleId}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<List<VehicleHireResponseDto>> byVehicle(@PathVariable Long vehicleId,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(vehicleHireService.getHiresByVehicle(vehicleId, currentUser(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleHireResponseDto> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(vehicleHireService.getHireById(id, currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}

