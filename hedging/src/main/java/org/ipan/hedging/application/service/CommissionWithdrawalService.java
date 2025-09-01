package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.repository.CommissionWithdrawalRepository;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@AllArgsConstructor
@Slf4j
public class CommissionWithdrawalService {
    private final UserRepo userRepo;
    private final CommissionWithdrawalRepository commissionWithdrawalRepository;

    @Transactional
    public CommissionWithdrawal createCommissionWithdrawal(Long userId, String instrument, BigDecimal amount, Instant date) {
        AppUser user = userRepo.findById(userId).get();
        AppUserInstrument userInstrument = user.findUserInstrument(instrument);
        CommissionWithdrawal result = createUserBalanceAction(userInstrument, amount, date);
        result = commissionWithdrawalRepository.save(result);
        return result;
    }

    private CommissionWithdrawal createUserBalanceAction(AppUserInstrument instrument, BigDecimal amount, Instant date) {
        CommissionWithdrawal commissionWithdrawal = new CommissionWithdrawal();
        commissionWithdrawal.setDate(date);
        commissionWithdrawal.setAmount(amount);
        commissionWithdrawal.setUserInstrument(instrument);
        return commissionWithdrawal;
    }
}
