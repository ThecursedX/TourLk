package com.tourlk.controller;

import com.tourlk.dto.GuideReplyRequestDto;
import com.tourlk.dto.GuideReviewStatsDto;
import com.tourlk.dto.RatingSummaryDto;
import com.tourlk.dto.ReviewEditHistoryResponseDto;
import com.tourlk.dto.ReviewRequestDto;
import com.tourlk.dto.ReviewResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.ReviewStatus;
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
 *Review creation/management for completed bookings, reservations and
 *hires, plus public browsing and rating summaries. Mirrors the Payment
 *module's conventions (one entity + type discriminator across multiple
 *reviewable things).
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

    /** The reviewer who owns this review, or an ADMIN; ownership is enforced in the service. */
    @GetMapping("/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ReviewEditHistoryResponseDto>> history(@PathVariable Long id,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(reviewService.getEditHistory(id, currentUser(authentication)));
    }

    /** GUIDE (owner of the reviewed package) or ADMIN; ownership is enforced in the service. */
    @PutMapping("/{id}/reply")
    @PreAuthorize("hasAnyRole('GUIDE','ADMIN')")
    public ResponseEntity<ReviewResponseDto> reply(@PathVariable Long id,
                                                    @Valid @RequestBody GuideReplyRequestDto request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(reviewService.replyToReview(id, request, currentUser(authentication)));
    }

    @DeleteMapping("/{id}/reply")
    @PreAuthorize("hasAnyRole('GUIDE','ADMIN')")
    public ResponseEntity<ReviewResponseDto> removeReply(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(reviewService.removeGuideReply(id, currentUser(authentication)));
    }

    /** Average rating, per-star breakdown and reply rate across every package the caller owns. */
    @GetMapping("/guide/stats")
    @PreAuthorize("hasRole('GUIDE')")
    public ResponseEntity<GuideReviewStatsDto> guideStats(Authentication authentication) {
        return ResponseEntity.ok(reviewService.getGuideStats(currentUser(authentication)));
    }

    /** ADMIN moderation view; pass {@code status} to narrow to one ReviewStatus. */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReviewResponseDto>> allForAdmin(@RequestParam(required = false) ReviewStatus status) {
        return ResponseEntity.ok(reviewService.getAllReviewsForAdmin(status));
    }

    @PutMapping("/{id}/report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponseDto> report(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.reportReview(id));
    }

    @PutMapping("/{id}/unreport")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponseDto> unreport(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.unreportReview(id));
    }

    @PutMapping("/{id}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponseDto> hide(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.hideReview(id));
    }

    @PutMapping("/{id}/unhide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReviewResponseDto> unhide(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.unhideReview(id));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
