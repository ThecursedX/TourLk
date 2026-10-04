package com.tourlk.service;

import com.tourlk.entity.Vehicle;
import com.tourlk.enums.NotificationType;
import com.tourlk.enums.VehicleStatus;
import com.tourlk.repo.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VehicleReminderJob}: the 14-day insurance and
 * maintenance window, in-app admin notifications only, operating vehicles only.
 */
@ExtendWith(MockitoExtension.class)
class VehicleReminderJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private VehicleReminderJob job;

    private Vehicle vehicle(long id) {
        return Vehicle.builder().id(id).make("Toyota").model("HiAce").registrationNumber("NA-" + id)
                .status(VehicleStatus.AVAILABLE).build();
    }

    private void stubDue(List<Vehicle> insuranceDue, List<Vehicle> maintenanceDue) {
        when(vehicleRepository.findByStatusInAndInsuranceExpiryLessThanEqual(any(), any())).thenReturn(insuranceDue);
        when(vehicleRepository.findByStatusInAndNextMaintenanceDateLessThanEqual(any(), any()))
                .thenReturn(maintenanceDue);
    }

    @Test
    void sendReminders_queriesFourteenDayWindowForOperatingVehiclesOnly() {
        stubDue(List.of(), List.of());

        job.sendReminders(TODAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<VehicleStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(vehicleRepository).findByStatusInAndInsuranceExpiryLessThanEqual(
                statuses.capture(), eq(TODAY.plusDays(14)));
        verify(vehicleRepository).findByStatusInAndNextMaintenanceDateLessThanEqual(any(), eq(TODAY.plusDays(14)));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                VehicleStatus.AVAILABLE, VehicleStatus.BOOKED, VehicleStatus.UNDER_MAINTENANCE);
    }

    @Test
    void sendReminders_insuranceDueSoon_notifiesAdminsWithLinkToVehicle() {
        Vehicle v = vehicle(20L);
        v.setInsuranceExpiry(TODAY.plusDays(5));
        stubDue(List.of(v), List.of());

        int sent = job.sendReminders(TODAY);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyAdmins(eq(NotificationType.VEHICLE_INSURANCE_DUE), any(),
                message.capture(), eq("/vehicles/20"));
        assertThat(message.getValue()).contains("NA-20").contains("due in 5 day(s)");
    }

    @Test
    void sendReminders_maintenanceOverdue_notifiesAdminsAsOverdue() {
        Vehicle v = vehicle(21L);
        v.setNextMaintenanceDate(TODAY.minusDays(3));
        stubDue(List.of(), List.of(v));

        int sent = job.sendReminders(TODAY);

        assertThat(sent).isEqualTo(1);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyAdmins(eq(NotificationType.VEHICLE_MAINTENANCE_DUE), title.capture(),
                any(), eq("/vehicles/21"));
        assertThat(title.getValue()).contains("overdue");
    }

    @Test
    void sendReminders_dueToday_saysDueToday() {
        Vehicle v = vehicle(22L);
        v.setInsuranceExpiry(TODAY);
        stubDue(List.of(v), List.of());

        job.sendReminders(TODAY);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyAdmins(any(), title.capture(), any(), any());
        assertThat(title.getValue()).contains("due today");
    }

    @Test
    void sendReminders_vehicleWithBothDue_raisesOneNotificationPerItem() {
        Vehicle v = vehicle(23L);
        v.setInsuranceExpiry(TODAY.plusDays(1));
        v.setNextMaintenanceDate(TODAY.plusDays(14));
        stubDue(List.of(v), List.of(v));

        assertThat(job.sendReminders(TODAY)).isEqualTo(2);
    }

    @Test
    void sendReminders_nothingDue_sendsNothing() {
        stubDue(List.of(), List.of());

        assertThat(job.sendReminders(TODAY)).isZero();
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
    }
}
