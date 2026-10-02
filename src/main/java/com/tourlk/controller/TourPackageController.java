package com.tourlk.controller;

import com.tourlk.dto.PackageSearchCriteria;
import com.tourlk.dto.RejectPackageRequestDto;
import com.tourlk.dto.TourPackageRequestDto;
import com.tourlk.dto.TourPackageResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.BudgetTier;
import com.tourlk.enums.PackageSort;
import com.tourlk.enums.PackageStatus;
import com.tourlk.service.TourPackageService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
import java.time.LocalDate;
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
                                                           @RequestParam(defaultValue = "false") boolean confirmChanges,
                                                           Authentication authentication) {
        return ResponseEntity.ok(
                tourPackageService.updatePackage(id, request, currentUser(authentication), confirmChanges));
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
    public ResponseEntity<TourPackageResponseDto> reject(@PathVariable Long id,
                                                           @Valid @RequestBody RejectPackageRequestDto request) {
        return ResponseEntity.ok(tourPackageService.rejectPackage(id, request.getReason()));
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

    /**
     * Public browse of ACTIVE packages. Every parameter is optional:
     * {@code q} matches title/description, {@code budgetTier} is
     * BUDGET/STANDARD/LUXURY by price per day, {@code travelDate} keeps
     * packages departing on/after it (packages without departures always
     * match), and {@code sort} is one of price_asc, price_desc, duration,
     * rating, newest.
     */
    @GetMapping
    public ResponseEntity<List<TourPackageResponseDto>> browse(
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer minDays,
            @RequestParam(required = false) Integer maxDays,
            @RequestParam(required = false) BudgetTier budgetTier,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate travelDate,
            @RequestParam(required = false) String sort) {
        PackageSearchCriteria criteria = PackageSearchCriteria.builder()
                .destinationId(destinationId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .q(q)
                .minDays(minDays)
                .maxDays(maxDays)
                .budgetTier(budgetTier)
                .travelDate(travelDate)
                .sort(PackageSort.fromParam(sort))
                .build();
        return ResponseEntity.ok(tourPackageService.browsePackages(criteria));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourPackageResponseDto> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(tourPackageService.getPackageById(id, optionalCurrentUser(authentication)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TourPackageResponseDto>> adminList(
            @RequestParam(required = false) PackageStatus status) {
        return ResponseEntity.ok(tourPackageService.getAllPackagesForAdmin(status));
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

    /** Null for anonymous callers of public endpoints. */
    private User optionalCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return currentUser(authentication);
    }

}
