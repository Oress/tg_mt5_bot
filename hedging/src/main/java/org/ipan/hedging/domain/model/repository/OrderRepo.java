package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.Order;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepo extends JpaRepository<Order, Long> {
    @Query("select o from Order o join o.userInstrument ui join ui.user u join ui.instrument i where i.label = :label and o.status = 'COMPLETE' and o.createdAt > :syncDt and u.telegramId = :telegramId order by o.createdAt")
    List<Order> findAllForSyncByInstrument(Instant syncDt, Long telegramId, String label);

/*
    @Query("select o from Order o join o.userInstrument ui join ui.user u join ui.instrument i where o.quoteAmountLeftToUnhedge > 0 and i.label = :symbol and u.telegramId = :telegramId order by o.createdAt")
    List<Order> findAllForUnhendging(Long telegramId, String symbol);
*/

    @Query("select o from Order o left join fetch o.consumptions where o.id = :id")
    Order findOrderWithConsumptions(Long id);

    @Query("select o from Order o left join fetch o.housePositionConsumptions where o.id = :id")
    Order findOrderWithHouseConsumptions(Long id);

    @Query("select o from Order o join o.userInstrument ui where ui.id = :userInstrumentId order by o.createdAt asc")
    List<Order> findAllForReportingCalculation(Long userInstrumentId);

    Page<Order> findAllByUserInstrumentIdOrderByCreatedAtDesc(Long userInstrumentId, Pageable pageable);
    List<Order> findAllByUserInstrumentIdOrderByCreatedAtDesc(Long userInstrumentId);

    @Query("select o from Order o join o.userInstrument ui left join fetch o.consumptions where ui.id = :positionId and o.lifoPosition is not null and o.lifoPosition.remainingAmount > 0 order by o.createdAt desc")
    List<Order> findOpenPositions(Long positionId);

    @Query("select sum(o.swap) from Order o where o.userInstrument.id = :userInstrumentId")
    BigDecimal getSwapPerInstrument(Long userInstrumentId);

    @Query("select o from Order o where o.swap is null and o.deal is not null")
    List<Order> findOrdersWithEmptySwap();
}
