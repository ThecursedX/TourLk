package com.tourlk.controller;

import com.tourlk.dto.BookingRequestDto;
import com.tourlk.dto.BookingResponseDto;
import com.tourlk.dto.RescheduleRequestDto;
import com.tourlk.entity.User;
import com.tourlk.service.BookingService;
import com.tourlk.service.UserService;
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
 * Booking creation, confirmation, reschedule and cancellation workflow.
 * Follows the same layer-first + module-prefixed-filename convention as
 * the Tour Package module.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create and manage tour package bookings")
public class BookingController {

    private final BookingService bookingService;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<BookingResponseDto> create(@Valid @RequestBody BookingRequestDto request,
                                                       Authentication authentication) {
        BookingResponseDto response = bookingService.createBooking(request, currentUser(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.confirmBooking(id));
    }

    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<BookingResponseDto> requestReschedule(@PathVariable Long id,
                                                                  @Valid @RequestBody RescheduleRequestDto request,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(
                bookingService.requestReschedule(id, request, currentUser(authentication)));
    }

    @PutMapping("/{id}/reschedule/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> approveReschedule(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.approveReschedule(id));
    }

    @PutMapping("/{id}/reschedule/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> rejectReschedule(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.rejectReschedule(id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('TOURIST','ADMIN')")
    public ResponseEntity<BookingResponseDto> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(bookingService.cancelBooking(id, currentUser(authentication)));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingResponseDto> complete(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.completeBooking(id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TOURIST')")
    public ResponseEntity<List<BookingResponseDto>> mine(Authentication authentication) {
        User currentUser = currentUser(authentication);
        return ResponseEntity.ok(bookingService.getBookingsByTourist(currentUser.getId()));
    }

    @GetMapping("/package/{packageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BookingResponseDto>> byPackage(@PathVariable Long packageId) {
        return ResponseEntity.ok(bookingService.getBookingsByPackage(packageId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDto> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(bookingService.getBookingById(id, currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
