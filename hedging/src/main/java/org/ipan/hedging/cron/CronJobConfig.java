package org.ipan.hedging.cron;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@AllArgsConstructor
@Slf4j
@Profile("!test")
public class CronJobConfig {
    private final TelegramAuditJob telegramAuditJob;
    private final RefreshCommonSheetsJob refreshCommonSheetsJob;
    private final FillOrderInfoFromMt5Job fillOrderInfoFromMt5Job;

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

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void refeshCommonSheet() {
        try {
            refreshCommonSheetsJob.run();
        } catch (Exception e) {
            log.error("Error while refreshing common sheets", e);
        }
    }

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    public void fetchOrderInfoForMissedOrders() {
        try {
            fillOrderInfoFromMt5Job.run();
        } catch (Exception e) {
            log.error("Error while fetchOrderInfoForMissedOrders", e);
        }
    }
}
