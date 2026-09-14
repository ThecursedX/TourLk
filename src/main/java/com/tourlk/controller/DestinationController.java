package com.tourlk.controller;

import com.tourlk.dto.DestinationRequestDto;
import com.tourlk.dto.DestinationResponseDto;
import com.tourlk.service.DestinationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
     * Public browse — active destinations only. Optional {@code search}
     * (name contains, case-insensitive) or {@code region} (exact) filter;
     * if both are given, {@code search} wins.
     */
    @GetMapping
    public ResponseEntity<List<DestinationResponseDto>> browse(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String region) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(destinationService.searchByName(search));
        }
        if (region != null && !region.isBlank()) {
            return ResponseEntity.ok(destinationService.getByRegion(region));
        }
        return ResponseEntity.ok(destinationService.getAllActive());
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DestinationResponseDto>> getAll() {
        return ResponseEntity.ok(destinationService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DestinationResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.getById(id));
    }

}
