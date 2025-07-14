package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepo extends JpaRepository<Order, UUID> {
    @Query("select o from Order o join o.userInstrument ui join ui.user u join ui.instrument i where i.label = :symbol and o.createdAt > :syncDt and u.telegramId = :telegramId order by o.createdAt")
    List<Order> findAllForSyncByInstrument(Instant syncDt, Long telegramId, String symbol);

    @Query("select o from Order o join o.userInstrument ui join ui.user u join ui.instrument i where o.quoteAmountLeftToUnhedge > 0 and i.label = :symbol and u.telegramId = :telegramId order by o.createdAt")
    List<Order> findAllForUnhendging(Long telegramId, String symbol);

    @Query("select o from Order o join fetch o.consumptions where o.id = :id")
    Order findOrderWithConsumptions(UUID id);
}
