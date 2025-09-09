package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.BalanceActionType;
import org.ipan.hedging.domain.model.UserBalanceAction;
import org.ipan.hedging.domain.model.repository.AppUserInstrumentRepository;
import org.ipan.hedging.domain.model.repository.UserBalanceActionRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.ReportingCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class UserBalanceService {
    private final ReportingCalculationService reportingCalculationService;
    private final Optional<GoogleSheetsService> googleSheetsService;
    private final UserRepo userRepo;
    private final UserBalanceActionRepo userBalanceActionRepo;

    @Transactional
    public UserBalanceAction createDepositAction(Long userId, BigDecimal amount, Instant date) {
        AppUser user = userRepo.findById(userId).get();
        UserBalanceAction result = createUserBalanceAction(user, BalanceActionType.DEPOSIT, amount, date);
        result = userBalanceActionRepo.save(result);
        // reportingCalculationService.recalculateAllForUser(userId);
        googleSheetsService.ifPresent((srv) -> srv.syncUser(userId));
        return result;
    }

    @Transactional
    public UserBalanceAction createWithdrawAction(Long userId, BigDecimal amount, Instant date) {
        AppUser user = userRepo.findById(userId).get();
        UserBalanceAction result = createUserBalanceAction(user, BalanceActionType.WITHDRAWAL, amount, date);
        result = userBalanceActionRepo.save(result);
        // reportingCalculationService.recalculateAllForUserInstrument(userId);
        googleSheetsService.ifPresent((srv) -> srv.syncUser(userId));
        return result;
    }

    private UserBalanceAction createUserBalanceAction(AppUser user, BalanceActionType type, BigDecimal amount, Instant date) {
        UserBalanceAction userBalanceAction = new UserBalanceAction();
        userBalanceAction.setType(type);
        userBalanceAction.setDate(date);
        userBalanceAction.setAmount(amount);
        userBalanceAction.setUser(user);

        if (type == BalanceActionType.DEPOSIT) {
            user.addBalanceUsd(amount);
        } else if (type == BalanceActionType.WITHDRAWAL) {
            user.subtractBalanceUsd(amount);
        } else {
            throw new IllegalArgumentException("Unsupported balance action type: " + type);
        }

        return userBalanceAction;
    }
}
