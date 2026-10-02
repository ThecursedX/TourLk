package com.tourlk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Reopens TEMPORARILY_CLOSED destinations once their closure end date has passed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationReopenJob {

    private final DestinationService destinationService;

    @Scheduled(cron = "${app.destination-reopen.cron:0 10 0 * * *}")
    public void reopenExpiredClosures() {
        try {
            int reopened = destinationService.reopenExpiredClosures(LocalDate.now());
            if (reopened > 0) {
                log.info("Reopened {} destination(s) after their closure ended", reopened);
            }
        } catch (RuntimeException e) {
            log.warn("Destination reopen job failed", e);
        }
    }

}
