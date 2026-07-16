package com.cax.cax_backend.manualverification.scheduler;

import com.cax.cax_backend.manualverification.service.ManualVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Yearly re-verification for manually (ID-card) verified accounts.
 * Approvals are valid until July 19 (Asia/Kolkata); this runs daily and
 * (a) reminds users a week ahead, (b) flags overdue accounts for
 * re-verification with a 30-day grace period, (c) expires accounts after
 * the grace period. Users are never logged out mid-session — the state
 * only affects routing on next app open.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ManualVerificationScheduler {

    private final ManualVerificationService service;

    // Daily at 03:30 IST (22:00 UTC)
    @Scheduled(cron = "0 0 22 * * *")
    public void runDailyRevalidation() {
        log.info("Running manual-verification yearly revalidation check...");
        try {
            service.sendExpiryReminders();
        } catch (Exception e) {
            log.error("Expiry reminder run failed: {}", e.getMessage(), e);
        }
        try {
            service.processExpiredApprovals();
        } catch (Exception e) {
            log.error("Expired-approval processing failed: {}", e.getMessage(), e);
        }
    }
}
