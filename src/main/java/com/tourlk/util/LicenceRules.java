package com.tourlk.util;

import com.tourlk.entity.User;
import com.tourlk.enums.VerificationStatus;
import com.tourlk.exception.LicenceNotVerifiedException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Licence expiry is computed, never stored as a status: a VERIFIED user whose
 * licenceExpiry is before today is treated as not verified for new listings,
 * and may renew once the licence is expired or within {@value #RENEWAL_WINDOW_DAYS} days of expiring.
 */
public final class LicenceRules {

    public static final int RENEWAL_WINDOW_DAYS = 30;

    private LicenceRules() {
    }

    /** True when the user is VERIFIED but the licence expiry date has passed. */
    public static boolean isExpired(User user, LocalDate today) {
        return user.getVerificationStatus() == VerificationStatus.VERIFIED
                && user.getLicenceExpiry() != null
                && user.getLicenceExpiry().isBefore(today);
    }

    /** Days until expiry (negative once expired); null when there is no expiry date. */
    public static Integer daysUntilExpiry(User user, LocalDate today) {
        if (user.getLicenceExpiry() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, user.getLicenceExpiry());
    }

    /** A VERIFIED licence may only be resubmitted when expired or expiring within the renewal window. */
    public static boolean canRenew(User user, LocalDate today) {
        Integer days = daysUntilExpiry(user, today);
        return days != null && days <= RENEWAL_WINDOW_DAYS;
    }

    /**
     * Gate for creating/submitting guide or driver listings.
     *
     * @throws LicenceNotVerifiedException with {@code notVerifiedMessage} when not VERIFIED,
     *                                     or an "expired" message when VERIFIED but past expiry
     */
    public static void assertVerifiedAndCurrent(User user, String notVerifiedMessage, LocalDate today) {
        if (user.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new LicenceNotVerifiedException(notVerifiedMessage);
        }
        if (isExpired(user, today)) {
            throw new LicenceNotVerifiedException(
                    "Your licence expired on " + user.getLicenceExpiry() + ". Renew it on your profile.");
        }
    }

}
