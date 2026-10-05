package com.tourlk.controller;

import com.tourlk.dto.ClosureImpactDto;
import com.tourlk.dto.DestinationClosureRequestDto;
import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.enums.Province;
import com.tourlk.service.DestinationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Destination catalogue: ADMIN-curated list of real places
 * (cities/regions/landmarks) that Tour Packages and Accommodations
 * reference. Mirrors the Tour Package module's layer-first +
 * module-prefixed-filename conventions.
 */
@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
@Tag(name = "Destinations", description = "Admin-curated list of places tourists can browse and filter by")
public class DestinationController {

    private final DestinationService destinationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> create(@Valid @RequestBody DestinationRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(destinationService.createDestination(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> update(@PathVariable Long id,
                                                         @Valid @RequestBody DestinationRequestDto request) {
        return ResponseEntity.ok(destinationService.updateDestination(id, request));
    }

    @PutMapping("/{id}/submit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> submit(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.submitForReview(id));
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> publish(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.publishDestination(id));
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> close(@PathVariable Long id,
                                                        @Valid @RequestBody DestinationClosureRequestDto request) {
        return ResponseEntity.ok(destinationService.closeTemporarily(
                id, request.getReason(), request.getFrom(), request.getUntil()));
    }

    /** Preview for the close confirmation: active bookings in the window that closing would cancel and refund. */
    @GetMapping("/{id}/closure-impact")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClosureImpactDto> closureImpact(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate until) {
        return ResponseEntity.ok(destinationService.previewClosureImpact(id, from, until));
    }

    @PutMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> reopen(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.reopenDestination(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> archive(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.archiveDestination(id));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.deactivateDestination(id));
    }

    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DestinationResponseDto> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.reactivateDestination(id));
    }

    /**
     * Public browse — PUBLISHED and TEMPORARILY_CLOSED destinations only.
     * Optional {@code nearby=lat,lng} (with {@code radiusKm}, default 50)
     * returns destinations within that distance, nearest first; otherwise
     * optional {@code search} (name contains, case-insensitive) or
     * {@code province} (e.g. {@code SOUTHERN}) filter; if both are given,
     * {@code search} wins. {@code nearby} takes precedence over both.
     */
    @GetMapping
    public ResponseEntity<List<DestinationResponseDto>> browse(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Province province,
            @RequestParam(required = false) String nearby,
            @RequestParam(required = false) Double radiusKm) {
        if (nearby != null && !nearby.isBlank()) {
            return ResponseEntity.ok(destinationService.searchNearby(nearby, radiusKm));
        }
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(destinationService.searchByName(search));
        }
        if (province != null) {
            return ResponseEntity.ok(destinationService.getByProvince(province));
        }
        return ResponseEntity.ok(destinationService.getAllActive());
    }

    /**
     * Category suggestions for the admin destination form. Declared before
     * {@code /{id}}; Spring prefers the literal path either way.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(destinationService.getCategorySuggestions());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DestinationResponseDto>> getAll() {
        return ResponseEntity.ok(destinationService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DestinationResponseDto> getById(@PathVariable Long id, Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(destinationService.getById(id, isAdmin));
    }

}
