package com.tourlk.service;

import com.tourlk.dto.NotificationResponseDto;
import com.tourlk.entity.Notification;
import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.exception.ResourceNotFoundException;
import com.tourlk.repo.NotificationRepository;
import com.tourlk.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationServiceImpl}: best-effort creation
 * (never throws, even when the repository does), the recipient-only
 * access guard, and the mine/unread-count/mark-read(-all) queries.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationServiceImpl service;

    private User recipient;
    private User stranger;

    @BeforeEach
    void setUp() {
        recipient = User.builder().id(1L).name("Tess").email("tess@example.com").role(Role.TOURIST).build();
        stranger = User.builder().id(2L).name("Stan").role(Role.TOURIST).build();
    }

    private Notification notification(User owner, boolean read) {
        return Notification.builder()
                .id(7L).recipient(owner).type(NotificationType.BOOKING_CREATED)
                .title("New booking").message("Someone booked your package").linkUrl("/bookings/5")
                .read(read)
                .build();
    }

    @Nested
    class Notify {

        @Test
        void notify_savesNotificationForRecipient() {
            service.notify(recipient, NotificationType.BOOKING_CONFIRMED, "Confirmed", "Your booking is confirmed",
                    "/bookings/5");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getRecipient()).isSameAs(recipient);
            assertThat(captor.getValue().getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
            assertThat(captor.getValue().getTitle()).isEqualTo("Confirmed");
        }

        @Test
        void notify_repositoryThrows_isSwallowedNotPropagated() {
            when(notificationRepository.save(any(Notification.class))).thenThrow(new RuntimeException("db down"));

            assertThatCode(() -> service.notify(recipient, NotificationType.BOOKING_CONFIRMED, "t", "m", null))
                    .doesNotThrowAnyException();
        }

        @Test
        void notifyAdmins_notifiesEveryAdmin() {
            User admin1 = User.builder().id(9L).role(Role.ADMIN).build();
            User admin2 = User.builder().id(10L).role(Role.ADMIN).build();
            when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin1, admin2));

            service.notifyAdmins(NotificationType.PACKAGE_SUBMITTED, "Submitted", "A package was submitted", null);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(Notification::getRecipient)
                    .containsExactly(admin1, admin2);
        }
    }

    @Nested
    class Queries {

        @Test
        void getMyNotifications_mapsLatest50ForRecipient() {
            when(notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(notification(recipient, false)));

            List<NotificationResponseDto> result = service.getMyNotifications(recipient);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("New booking");
            assertThat(result.get(0).isRead()).isFalse();
        }

        @Test
        void getUnreadCount_returnsRepositoryCount() {
            when(notificationRepository.countByRecipientIdAndReadFalse(1L)).thenReturn(3L);

            assertThat(service.getUnreadCount(recipient)).isEqualTo(3L);
        }
    }

    @Nested
    class MarkRead {

        @Test
        void markRead_byRecipient_setsReadTrue() {
            Notification n = notification(recipient, false);
            when(notificationRepository.findById(7L)).thenReturn(Optional.of(n));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

            NotificationResponseDto result = service.markRead(7L, recipient);

            assertThat(result.isRead()).isTrue();
        }

        @Test
        void markRead_byStranger_throwsAccessDenied() {
            when(notificationRepository.findById(7L)).thenReturn(Optional.of(notification(recipient, false)));

            assertThatThrownBy(() -> service.markRead(7L, stranger))
                    .isInstanceOf(AccessDeniedException.class);
            verify(notificationRepository, never()).save(any());
        }

        @Test
        void markRead_notFound_throwsResourceNotFound() {
            when(notificationRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markRead(404L, recipient))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void markAllRead_marksEveryUnreadNotificationForRecipient() {
            Notification n1 = notification(recipient, false);
            Notification n2 = notification(recipient, false);
            when(notificationRepository.findByRecipientIdAndReadFalse(1L)).thenReturn(List.of(n1, n2));

            service.markAllRead(recipient);

            assertThat(n1.isRead()).isTrue();
            assertThat(n2.isRead()).isTrue();
            verify(notificationRepository).saveAll(List.of(n1, n2));
        }
    }
}
