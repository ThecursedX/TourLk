package com.tourlk.controller;

import com.tourlk.dto.VehicleRequestDto;
import com.tourlk.dto.VehicleResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.VehicleType;
import com.tourlk.service.UserService;
import com.tourlk.service.VehicleService;
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
 * Vehicle registration, approval workflow and public browsing.
 * Mirrors the Tour Package / Accommodation modules' conventions.
 */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Register, manage and browse hire vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<VehicleResponseDto> create(@Valid @RequestBody VehicleRequestDto request,
                                                     Authentication authentication) {
        VehicleResponseDto response = vehicleService.createVehicle(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<VehicleResponseDto> update(@PathVariable Long id,
                                                     @Valid @RequestBody VehicleRequestDto request,
                                                     Authentication authentication) {
        return ResponseEntity.ok(vehicleService.updateVehicle(id, request, currentUser(authentication)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleResponseDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.approveVehicle(id));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VehicleResponseDto> reject(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.rejectVehicle(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<VehicleResponseDto> deactivate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(vehicleService.deactivateVehicle(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<VehicleResponseDto> reactivate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(vehicleService.reactivateVehicle(id, currentUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DRIVER')")
    public ResponseEntity<VehicleResponseDto> archive(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(vehicleService.archiveVehicle(id, currentUser(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<VehicleResponseDto>> browse(
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) Integer minSeatingCapacity) {
        return ResponseEntity.ok(vehicleService.getAllActive(vehicleType, minSeatingCapacity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehicleResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(vehicleService.getById(id));
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VehicleResponseDto>> pendingApproval() {
        return ResponseEntity.ok(vehicleService.getPendingApproval());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<List<VehicleResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(vehicleService.getByDriver(currentUser.getId()));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
