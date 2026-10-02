package com.tourlk.service;

import com.tourlk.dto.NotificationResponseDto;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;

import java.util.List;

public interface NotificationService {

    /** Creates a notification for a single recipient. Best-effort — never throws. */
    void notify(User recipient, NotificationType type, String title, String message, String linkUrl);

    /** Creates a notification for every ADMIN user. Best-effort — never throws. */
    void notifyAdmins(NotificationType type, String title, String message, String linkUrl);

    List<NotificationResponseDto> getMyNotifications(User currentUser);

    long getUnreadCount(User currentUser);

    NotificationResponseDto markRead(Long id, User currentUser);

    void markAllRead(User currentUser);

}
