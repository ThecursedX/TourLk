package com.tourlk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "Fully booked" is defined against today's date, so it goes stale at
 * midnight even if no reservation changes. This re-derives
 * ACTIVE / FULLY_BOOKED for every live accommodation shortly after
 * midnight; reservation confirm/cancel/complete keep it fresh in between.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccommodationAvailabilityJob {

    private final AccommodationService accommodationService;

    @Scheduled(cron = "${app.accommodation-availability.cron:0 5 0 * * *}")
    public void refreshAvailability() {
        try {
            accommodationService.refreshAllAvailabilityStatuses();
        } catch (RuntimeException e) {
            log.warn("Accommodation availability refresh failed", e);
        }
    }

}
