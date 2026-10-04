package com.tourlk.controller;

import com.tourlk.dto.PackageDepartureRequestDto;
import com.tourlk.dto.PackageDepartureResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.PackageDepartureService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Scheduled departure dates of a tour package. Listing is public; adding
 * and removing is limited to the package owner or an admin (ownership is
 * checked in the service).
 */
@RestController
@RequestMapping("/api/packages/{packageId}/departures")
@RequiredArgsConstructor
@Tag(name = "Package Departures", description = "Manage and list a tour package's departure dates")
public class PackageDepartureController {

    private final PackageDepartureService departureService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<PackageDepartureResponseDto>> list(@PathVariable Long packageId) {
        return ResponseEntity.ok(departureService.getUpcomingDepartures(packageId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<PackageDepartureResponseDto> add(@PathVariable Long packageId,
                                                           @Valid @RequestBody PackageDepartureRequestDto request,
                                                           Authentication authentication) {
        PackageDepartureResponseDto response =
                departureService.addDeparture(packageId, request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{departureId}")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<Void> delete(@PathVariable Long packageId, @PathVariable Long departureId,
                                       Authentication authentication) {
        departureService.deleteDeparture(packageId, departureId, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
