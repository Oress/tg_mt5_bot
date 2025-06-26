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
    @Query("select o from Order o where o.instrument = :symbol and o.createdAt > :syncDt and o.user.telegramId = :telegramId order by o.createdAt")
    List<Order> findAllForSyncByInstrument(Instant syncDt, Long telegramId, String symbol);
}
