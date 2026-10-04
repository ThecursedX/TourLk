package com.tourlk.service;

import com.tourlk.entity.Vehicle;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.repo.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

/**
 * Daily in-app reminder to admins when a vehicle's insurance expires, or
 * its next maintenance is due, within {@value #WINDOW_DAYS} days (overdue
 * dates are included). There is no per-vehicle "already reminded" state,
 * so a vehicle inside the window is reminded about once per run (daily)
 * until the date is updated. Vehicles that aren't operating (draft,
 * pending, out of service, archived) are skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleReminderJob {

    static final int WINDOW_DAYS = 14;

    private static final Set<VehicleStatus> OPERATING =
            EnumSet.of(VehicleStatus.AVAILABLE, VehicleStatus.BOOKED, VehicleStatus.UNDER_MAINTENANCE);

    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.vehicle-reminders.cron:0 0 8 * * *}")
    public void sendDailyReminders() {
        int sent = sendReminders(LocalDate.now());
        if (sent > 0) {
            log.info("Sent {} vehicle insurance/maintenance reminder(s)", sent);
        }
    }

    /** @return how many notifications were raised (one per vehicle per due item). */
    int sendReminders(LocalDate today) {
        LocalDate cutoff = today.plusDays(WINDOW_DAYS);
        int sent = 0;

        for (Vehicle vehicle : vehicleRepository.findByStatusInAndInsuranceExpiryLessThanEqual(OPERATING, cutoff)) {
            notificationService.notifyAdmins(
                    NotificationType.VEHICLE_INSURANCE_DUE,
                    "Vehicle insurance " + timing(vehicle.getInsuranceExpiry(), today),
                    describe(vehicle) + ": insurance " + timing(vehicle.getInsuranceExpiry(), today)
                            + " (" + vehicle.getInsuranceExpiry() + ").",
                    "/vehicles/" + vehicle.getId());
            sent++;
        }

        for (Vehicle vehicle : vehicleRepository.findByStatusInAndNextMaintenanceDateLessThanEqual(OPERATING, cutoff)) {
            notificationService.notifyAdmins(
                    NotificationType.VEHICLE_MAINTENANCE_DUE,
                    "Vehicle maintenance " + timing(vehicle.getNextMaintenanceDate(), today),
                    describe(vehicle) + ": maintenance " + timing(vehicle.getNextMaintenanceDate(), today)
                            + " (" + vehicle.getNextMaintenanceDate() + ").",
                    "/vehicles/" + vehicle.getId());
            sent++;
        }

        return sent;
    }

    private String describe(Vehicle vehicle) {
        return vehicle.getMake() + " " + vehicle.getModel() + " (" + vehicle.getRegistrationNumber() + ")";
    }

    private String timing(LocalDate date, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, date);
        if (days < 0) {
            return "overdue";
        }
        return days == 0 ? "due today" : "due in " + days + " day(s)";
    }

}
