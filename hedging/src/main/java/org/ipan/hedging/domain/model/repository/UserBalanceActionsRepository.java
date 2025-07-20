package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.UserBalanceAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBalanceActionsRepository extends JpaRepository<UserBalanceAction, Long> {
    List<UserBalanceAction> findByUserInstrumentId(Long userInstrumentId);
    List<UserBalanceAction> findByUserInstrumentIdOrderByDateDesc(Long userInstrumentId);
}
