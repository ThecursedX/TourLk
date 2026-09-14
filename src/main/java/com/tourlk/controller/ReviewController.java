package com.tourlk.controller;

import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewRequestDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.ReviewableType;
import com.tourlk.service.ReviewService;
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
 * Review creation/management for completed bookings, reservations and
 * hires, plus public browsing and rating summaries. Mirrors the Payment
 * module's conventions (one entity + type discriminator across multiple
 * reviewable things).
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Leave and browse reviews for completed bookings, reservations and hires")
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<ReviewResponseDto> create(@Valid @RequestBody ReviewRequestDto request,
                                                     Authentication authentication) {
        ReviewResponseDto response = reviewService.createReview(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<ReviewResponseDto> update(@PathVariable Long id,
                                                     @Valid @RequestBody ReviewRequestDto request,
                                                     Authentication authentication) {
        return ResponseEntity.ok(reviewService.updateReview(id, request, currentUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TOURIST','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        reviewService.deleteReview(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ReviewResponseDto>> forItem(@RequestParam ReviewableType type,
                                                            @RequestParam Long id) {
        return ResponseEntity.ok(reviewService.getReviewsFor(type, id));
    }

    @GetMapping("/summary")
    public ResponseEntity<RatingSummaryDto> summary(@RequestParam ReviewableType type,
                                                      @RequestParam Long id) {
        return ResponseEntity.ok(reviewService.getRatingSummary(type, id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<List<ReviewResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(reviewService.getReviewsByUser(currentUser.getId()));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
