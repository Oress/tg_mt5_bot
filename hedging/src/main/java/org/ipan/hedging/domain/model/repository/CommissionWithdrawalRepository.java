package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.CommissionWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionWithdrawalRepository extends JpaRepository<CommissionWithdrawal, Long> {
    List<CommissionWithdrawal> findAllByUserInstrumentUserTelegramIdOrderByDateDesc(Long telegramId);
}
