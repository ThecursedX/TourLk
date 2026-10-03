package com.tourlk.service;

import com.tourlk.entity.User;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.Role;
import com.tourlk.enums.VerificationStatus;
import com.tourlk.repo.UserRepository;
import com.tourlk.util.LicenceRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Daily in-app reminders to GUIDE/DRIVER users with a VERIFIED licence: once
 * when it is within {@value LicenceRules#RENEWAL_WINDOW_DAYS} days of expiry
 * and once on the day it expires (or later, if a run was missed). The expiry date each notice was sent for is
 * stored on the user, so later runs (or a missed day) never duplicate it and
 * a renewal with a new expiry date starts the cycle afresh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LicenceExpiryReminderJob {

    private static final Set<Role> ROLES = EnumSet.of(Role.GUIDE, Role.DRIVER);

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.licence-reminders.cron:0 30 8 * * *}")
    public void sendDailyReminders() {
        try {
            int sent = sendReminders(LocalDate.now());
            if (sent > 0) {
                log.info("Sent {} licence expiry notification(s)", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Licence expiry reminder job failed", e);
        }
    }

    /** @return how many notifications were raised. */
    int sendReminders(LocalDate today) {
        LocalDate cutoff = today.plusDays(LicenceRules.RENEWAL_WINDOW_DAYS);
        List<User> due = userRepository.findByVerificationStatusAndRoleInAndLicenceExpiryLessThanEqual(
                VerificationStatus.VERIFIED, ROLES, cutoff);
        int sent = 0;

        for (User user : due) {
            LocalDate expiry = user.getLicenceExpiry();
            if (!expiry.isAfter(today)) {
                if (!expiry.equals(user.getLicenceExpiredNotifiedFor())) {
                    notificationService.notify(user, NotificationType.LICENCE_EXPIRED, "Licence expired",
                            (expiry.equals(today) ? "Your licence expires today" : "Your licence expired on " + expiry)
                                    + ". Renew it on your profile to keep creating packages or vehicles.",
                            "/profile");
                    user.setLicenceExpiredNotifiedFor(expiry);
                    userRepository.save(user);
                    sent++;
                }
            } else if (!expiry.equals(user.getLicenceExpiringNotifiedFor())) {
                long days = ChronoUnit.DAYS.between(today, expiry);
                notificationService.notify(user, NotificationType.LICENCE_EXPIRING, "Licence expiring soon",
                        "Your licence expires on " + expiry + " (" + (days == 0 ? "today" : "in " + days + " day(s)")
                                + "). Renew it on your profile.",
                        "/profile");
                user.setLicenceExpiringNotifiedFor(expiry);
                userRepository.save(user);
                sent++;
            }
        }
        return sent;
    }

}
