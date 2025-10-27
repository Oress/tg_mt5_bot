package org.ipan.hedging.cron;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.TerminalBroker;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUser_;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReassignTerminalsToUseForUsersJob {
    private final UserRepo userRepo;
    private final TerminalBroker terminalBroker;

    @PostConstruct
    public void run() throws IOException {
        log.info("ReassignTerminalsToUseForUsersJob started");

        List<AppUser> allUsers = userRepo.findAllByDisabled(false, Sort.by(Sort.Direction.ASC, AppUser_.CREATED_AT));
        for (AppUser user : allUsers) {
            user.setTerminalToUse(terminalBroker.getNextTerminalToUse());
            userRepo.save(user);
        }

        log.info("ReassignTerminalsToUseForUsersJob finished");
    }
}
