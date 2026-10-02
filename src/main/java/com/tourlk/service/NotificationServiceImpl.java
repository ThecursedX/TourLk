package com.tourlk.service;

import com.tourlk.dto.NotificationResponseDto;
import com.tourlk.entity.Notification;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.NotificationRepository;
import com.tourlk.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * In-app notifications. Creation ({@code notify}/{@code notifyAdmins}) is
 * always best-effort: it swallows and logs any failure so that a
 * notification row failing to save can never roll back or block the
 * booking/package/payment/ticket/review operation that triggered it —
 * same principle as {@code TicketMailService} for email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void notify(User recipient, NotificationType type, String title, String message, String linkUrl) {
        try {
            Notification notification = Notification.builder()
                    .recipient(recipient)
                    .type(type)
                    .title(title)
                    .message(message)
                    .linkUrl(linkUrl)
                    .build();
            notificationRepository.save(notification);
        } catch (RuntimeException e) {
            log.warn("Failed to create {} notification for user {}", type, recipient.getId(), e);
        }
    }

    @Override
    public void notifyAdmins(NotificationType type, String title, String message, String linkUrl) {
        for (User admin : userRepository.findByRole(Role.ADMIN)) {
            notify(admin, type, title, message, linkUrl);
        }
    }

    @Override
    public List<NotificationResponseDto> getMyNotifications(User currentUser) {
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long getUnreadCount(User currentUser) {
        return notificationRepository.countByRecipientIdAndReadFalse(currentUser.getId());
    }

    @Override
    @Transactional
    public NotificationResponseDto markRead(Long id, User currentUser) {
        Notification notification = getEntity(id);
        assertRecipient(notification, currentUser);

        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional
    public void markAllRead(User currentUser) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadFalse(currentUser.getId());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private Notification getEntity(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
    }

    private void assertRecipient(Notification notification, User currentUser) {
        if (!notification.getRecipient().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to access this notification");
        }
    }

    private NotificationResponseDto toResponse(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .linkUrl(notification.getLinkUrl())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

}
