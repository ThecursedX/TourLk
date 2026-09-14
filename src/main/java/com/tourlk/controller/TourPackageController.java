package com.tourlk.controller;

import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.TourPackageService;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * Tour package creation, approval workflow and public browsing.
 * Reference module for the layer-first + module-prefixed-filename
 * convention other modules (Booking, Payment, ...) should follow.
 */
@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
@Tag(name = "Tour Packages", description = "Create, manage and browse tour packages")
public class TourPackageController {

    private final TourPackageService tourPackageService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<TourPackageResponseDto> create(@Valid @RequestBody TourPackageRequestDto request,
                                                           Authentication authentication) {
        TourPackageResponseDto response = tourPackageService.createPackage(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<TourPackageResponseDto> update(@PathVariable Long id,
                                                           @Valid @RequestBody TourPackageRequestDto request,
                                                           Authentication authentication) {
        return ResponseEntity.ok(tourPackageService.updatePackage(id, request, currentUser(authentication)));
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<TourPackageResponseDto> submit(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(tourPackageService.submitForApproval(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TourPackageResponseDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(tourPackageService.approvePackage(id));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TourPackageResponseDto> reject(@PathVariable Long id) {
        return ResponseEntity.ok(tourPackageService.rejectPackage(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<TourPackageResponseDto> deactivate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(tourPackageService.deactivatePackage(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<TourPackageResponseDto> reactivate(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(tourPackageService.reactivatePackage(id, currentUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<TourPackageResponseDto> archive(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(tourPackageService.archivePackage(id, currentUser(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<TourPackageResponseDto>> browse(
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {
        boolean hasFilters = destinationId != null || minPrice != null || maxPrice != null;
        List<TourPackageResponseDto> results = hasFilters
                ? tourPackageService.searchPackages(destinationId, minPrice, maxPrice)
                : tourPackageService.getAllActivePackages();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourPackageResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tourPackageService.getPackageById(id));
    }

    @GetMapping("/pending-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TourPackageResponseDto>> pendingApproval() {
        return ResponseEntity.ok(tourPackageService.getPendingApprovalPackages());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMIN','GUIDE')")
    public ResponseEntity<List<TourPackageResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(tourPackageService.getPackagesByCreator(currentUser.getId()));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
