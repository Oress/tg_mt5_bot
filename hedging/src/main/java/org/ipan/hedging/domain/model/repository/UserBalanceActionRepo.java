package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.UserBalanceAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserBalanceActionRepo extends JpaRepository<UserBalanceAction, UUID> {

    @Query("select a from UserBalanceAction a join a.userInstrument ui join ui.user u join ui.instrument i where i.label = :label and a.date > :syncDt and u.telegramId = :telegramId order by a.createdAt")
    List<UserBalanceAction> findAllForSyncByInstrument(Instant syncDt, Long telegramId, String label);
}
