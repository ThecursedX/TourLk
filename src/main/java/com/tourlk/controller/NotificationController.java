package com.tourlk.controller;

import com.tourlk.dto.NotificationResponseDto;
import com.tourlk.dto.UnreadCountResponseDto;
import com.tourlk.entity.User;
import com.tourlk.service.NotificationService;
import com.tourlk.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * In-app notifications for the current user. Only the recipient can view
 * or mark their own notifications — enforced in {@code NotificationServiceImpl}.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the current user")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping("/mine")
    public ResponseEntity<List<NotificationResponseDto>> mine(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getMyNotifications(currentUser(authentication)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponseDto> unreadCount(Authentication authentication) {
        long count = notificationService.getUnreadCount(currentUser(authentication));
        return ResponseEntity.ok(UnreadCountResponseDto.builder().count(count).build());
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponseDto> markRead(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markRead(id, currentUser(authentication)));
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

}
