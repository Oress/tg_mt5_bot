package org.ipan.hedging.domain.service;

import org.ipan.hedging.application.service.TerminalBroker;
import org.ipan.hedging.domain.model.AccountType;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.domain.model.repository.AppUserInstrumentRepository;
import org.ipan.hedging.domain.model.repository.InstrumentRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.commands.users.CreateUserCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {
    private final UserRepo userRepo;
    private final InstrumentRepo instrumentRepo;
    private final PasswordService passwordService;
    private final AppUserInstrumentRepository appUserInstrumentRepository;
    private final TerminalBroker terminalBroker;

    @Transactional
    public AppUser createUser(CreateUserCommand command) {
        AppUser appUser = userRepo.findById(command.getTelegramId()).orElse(null);
        if (appUser != null) {
            return appUser;
        }

        appUser = new AppUser();
        updateModelFromCommand(command, appUser);
        appUser.setTerminalToUse(terminalBroker.getNextTerminalToUse());
        userRepo.save(appUser);

        return appUser;
    }

    @Transactional
    public AppUser updateUser(CreateUserCommand command) {
        AppUser appUser = userRepo.findById(command.getTelegramId()).orElse(null);

        updateModelFromCommand(command, appUser);

        return appUser;
    }

    @Transactional
    public AppUser setLanguage(Long userId, String language) {
        AppUser appUser = userRepo.findById(userId).orElse(null);

        if (!"en".equals(language) && !"ru".equals(language)) {
            throw new IllegalArgumentException("language is neither en nor ru");
        }

        appUser.setLanguageCode(language);

        return appUser;
    }

    @Transactional
    public void deactivateUser(Long selectedUserId) {
        AppUser appUser = userRepo.findById(selectedUserId).orElse(null);
        if (appUser != null) {
            appUser.setAccountType(null);
            appUser.setMt5AccNumber(null);
            appUser.setMt5AccPwdEnc(null);
            appUser.setDisabled(true);
        }
    }

    @Transactional
    public AppUser changeGerchikCreds(Long userId, int accountNumber, String accountPwd, AccountType accountType) {
        AppUser appUser = userRepo.findById(userId).orElse(null);

        appUser.setMt5AccNumber(accountNumber);
        appUser.setAccountType(accountType);
        appUser.setMt5AccPwdEnc(passwordService.encrypt(accountPwd));

        return appUser;
    }

    @Transactional
    public AppUserInstrument updateInstrumentCommission(Long userInstrumentId, BigDecimal newCommissionCoef) {
        if (newCommissionCoef == null || newCommissionCoef.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Percentage must be non null greater than 0, val: {}", newCommissionCoef);
            throw new IllegalArgumentException("Percentage must be non null greater than 0");
        }

        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();


        if (userInstrument.getActive()) {
            userInstrument.setCommissionCoef(newCommissionCoef);
        } else {
            throw new IllegalStateException("Instrument must be active to update commission");
        }

        return userInstrument;
    }

    @Transactional
    public AppUser toggleInstrument(Long userId, String userInstrumentLabel) {
        AppUser appUser = userRepo.findById(userId).orElse(null);
        Set<AppUserInstrument> availableInstruments = appUser.getAvailableInstruments();

        AppUserInstrument userInstrument = availableInstruments.stream().filter(i -> userInstrumentLabel.equals(i.getInstrument().getLabel())).findFirst().orElse(null);

        if (userInstrument != null) {
            if (userInstrument.getActive()) {
                userInstrument.setActive(false);
                log.info("Instument {} has been DISABLED for user {}", userInstrumentLabel, userId);
            } else {
                userInstrument.setActive(true);
                log.info("Instument {} has been ENABLED for user {}", userInstrumentLabel, userId);
            }
        } else {
            log.info("Instument {} has been CREATED for user {}", userInstrumentLabel, userId);
            Instrument instrument = instrumentRepo.findById(userInstrumentLabel).get();
            appUser.addInstrument(instrument);
        }

        return appUser;
    }

    @Transactional
    public AppUser toggleInstrument(Long userId, String userInstrumentLabel, boolean desiredState) {
        AppUser appUser = userRepo.findById(userId).orElse(null);
        Set<AppUserInstrument> availableInstruments = appUser.getAvailableInstruments();

        AppUserInstrument userInstrument = availableInstruments.stream().filter(i -> userInstrumentLabel.equals(i.getInstrument().getLabel())).findFirst().orElse(null);

        if (userInstrument != null) {
            if (userInstrument.getActive() && !desiredState) {
                userInstrument.setActive(false);
                log.info("Instument {} has been DISABLED for user {}", userInstrumentLabel, userId);
            } else if (desiredState) {
                userInstrument.setActive(true);
                log.info("Instument {} has been ENABLED for user {}", userInstrumentLabel, userId);
            } else {
                log.error("Cannot toggle instrument " + userInstrumentLabel + " to the same state for user " + userId);
            }
        } else if (desiredState) {
            log.info("Instument {} has been CREATED for user {}", userInstrumentLabel, userId);
            Instrument instrument = instrumentRepo.findById(userInstrumentLabel).get();
            appUser.addInstrument(instrument);
        } else {
            log.error("Cannot toggle instrument " + userInstrumentLabel + " to the same state for user " + userId);
        }

        return appUser;
    }

    private void updateModelFromCommand(CreateUserCommand command, AppUser appUser) {
        appUser.setTelegramId(command.getTelegramId());
        appUser.setTelegramUsername(command.getUsername());
        appUser.setTelegramFirstName(command.getFirstName());
        if (command.getLanguageCode() != null) {
            appUser.setLanguageCode(command.getLanguageCode());
        }
    }

    public AppUser getById(Long telegramId) {
        return userRepo.findById(telegramId).orElse(null);
    }
}
