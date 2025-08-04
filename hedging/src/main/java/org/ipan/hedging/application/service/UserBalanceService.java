package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.BalanceActionType;
import org.ipan.hedging.domain.model.UserBalanceAction;
import org.ipan.hedging.domain.model.repository.AppUserInstrumentRepository;
import org.ipan.hedging.domain.model.repository.UserBalanceActionRepo;
import org.ipan.hedging.domain.service.ReportingCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class UserBalanceService {
    private final ReportingCalculationService reportingCalculationService;
    private final Optional<GoogleSheetsService> googleSheetsService;
    private final AppUserInstrumentRepository appUserInstrumentRepository;
    private final UserBalanceActionRepo userBalanceActionRepo;

    public UserBalanceAction createDepositAction(Long userInstrumentId, BigDecimal amount, Instant date) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        UserBalanceAction result = createUserBalanceAction(userInstrument, BalanceActionType.DEPOSIT, amount, date);
        result = userBalanceActionRepo.save(result);
        reportingCalculationService.recalculateAllForUserInstrument(userInstrumentId);
        googleSheetsService.ifPresent((srv) -> srv.syncUser(userInstrument.getUser().getTelegramId()));
        return result;
    }

    public UserBalanceAction createWithdrawAction(Long userInstrumentId, BigDecimal amount, Instant date) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        UserBalanceAction result = createUserBalanceAction(userInstrument, BalanceActionType.WITHDRAWAL, amount, date);
        result = userBalanceActionRepo.save(result);
        reportingCalculationService.recalculateAllForUserInstrument(userInstrumentId);
        googleSheetsService.ifPresent((srv) -> srv.syncUser(userInstrument.getUser().getTelegramId()));
        return result;
    }

    private UserBalanceAction createUserBalanceAction(AppUserInstrument userInstrument, BalanceActionType type, BigDecimal amount, Instant date) {
        UserBalanceAction userBalanceAction = new UserBalanceAction();
        userBalanceAction.setType(type);
        userBalanceAction.setDate(date);
        userBalanceAction.setAmount(amount);
        userBalanceAction.setUserInstrument(userInstrument);

        return userBalanceAction;
    }
}
