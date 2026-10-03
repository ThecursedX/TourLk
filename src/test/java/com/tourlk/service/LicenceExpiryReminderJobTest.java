package com.tourlk.service;

import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.enums.VerificationStatus;
import com.tourlk.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link LicenceExpiryReminderJob}: 30-day warning and expiry notice, once each. */
@ExtendWith(MockitoExtension.class)
class LicenceExpiryReminderJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private LicenceExpiryReminderJob job;

    private User guide(LocalDate expiry) {
        return User.builder().id(2L).name("Gina").role(Role.GUIDE)
                .verificationStatus(VerificationStatus.VERIFIED).licenceExpiry(expiry).build();
    }

    private void stubDue(User... users) {
        when(userRepository.findByVerificationStatusAndRoleInAndLicenceExpiryLessThanEqual(
                eq(VerificationStatus.VERIFIED), any(), any())).thenReturn(List.of(users));
    }

    @Test
    void expiringWithin30Days_notifiesOnce() {
        User user = guide(TODAY.plusDays(30));
        stubDue(user);

        assertThat(job.sendReminders(TODAY)).isEqualTo(1);
        verify(notificationService).notify(eq(user), eq(NotificationType.LICENCE_EXPIRING), any(), any(), eq("/profile"));
        assertThat(user.getLicenceExpiringNotifiedFor()).isEqualTo(TODAY.plusDays(30));

        // A later run sees the recorded expiry date and stays quiet.
        assertThat(job.sendReminders(TODAY.plusDays(1))).isZero();
    }

    @Test
    void onTheDayItExpires_sendsExpiredNotice() {
        User user = guide(TODAY);
        user.setLicenceExpiringNotifiedFor(TODAY);
        stubDue(user);

        assertThat(job.sendReminders(TODAY)).isEqualTo(1);
        verify(notificationService).notify(eq(user), eq(NotificationType.LICENCE_EXPIRED), any(), any(), eq("/profile"));
        assertThat(user.getLicenceExpiredNotifiedFor()).isEqualTo(TODAY);
    }

    @Test
    void alreadyNotifiedForThisExpiry_isSkipped() {
        User user = guide(TODAY.minusDays(2));
        user.setLicenceExpiredNotifiedFor(TODAY.minusDays(2));
        stubDue(user);

        assertThat(job.sendReminders(TODAY)).isZero();
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void renewedLicenceWithNewExpiry_startsTheCycleAgain() {
        User user = guide(TODAY.plusDays(10));
        user.setLicenceExpiringNotifiedFor(TODAY.minusYears(1));
        stubDue(user);

        assertThat(job.sendReminders(TODAY)).isEqualTo(1);
    }

}
