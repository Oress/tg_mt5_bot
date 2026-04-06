package org.ipan.hedging.cron;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.GoogleSheetsService;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
@Profile("!test")
public class RefreshCommonSheetsJob {
    private final UserRepo userRepo;
    private final GoogleSheetsService googleSheetsService;

    @Transactional(readOnly = true)
    public void run() throws IOException {
        log.info("RefreshCommonSheetsJob started");

        List<AppUser> forCommonSheetSync = userRepo.findForCommonSheetSync();
        for (AppUser user : forCommonSheetSync) {
            log.info("refreshing data for the {}, {}", user.getTelegramId(), user.getTelegramUsername());
            googleSheetsService.syncCommonSheet(user);
        }

        log.info("RefreshCommonSheetsJob finished");
    }
}
