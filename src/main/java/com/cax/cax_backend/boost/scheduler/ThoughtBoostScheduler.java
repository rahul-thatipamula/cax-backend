package com.cax.cax_backend.boost.scheduler;

import com.cax.cax_backend.boost.service.ThoughtBoostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ThoughtBoostScheduler {

    private final ThoughtBoostService thoughtBoostService;

    // Every 3 hours: rotate boost slots
    @Scheduled(cron = "0 0 */3 * * *")
    public void rotateBoostSlots() {
        log.info("[BoostScheduler] Running 3-hour boost rotation");
        try {
            thoughtBoostService.runSchedulerWindow();
            log.info("[BoostScheduler] Rotation complete");
        } catch (Exception e) {
            log.error("[BoostScheduler] Rotation failed: {}", e.getMessage(), e);
        }
    }
}
