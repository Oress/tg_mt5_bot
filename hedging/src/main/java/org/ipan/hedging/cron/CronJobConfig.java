package org.ipan.hedging.cron;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@AllArgsConstructor
@Slf4j
public class CronJobConfig {
    private final TelegramAuditJob telegramAuditJob;

//    @Scheduled(fixedDelayString = "${app.cron.telegram-audit.fixed-rate:30}", timeUnit = TimeUnit.SECONDS)
    public void persistTelegramAudit() {
        try {
            if (telegramAuditJob.hasPendingCommands()) {
                telegramAuditJob.flushToDatabase();
            }
        } catch (Exception e) {
            log.error("Error while persisting Telegram audit messages", e);
        }
    }
}
