package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.ReportingCalculation;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface ReportingCalculationRepository extends JpaRepository<ReportingCalculation, Long> {

    @Query("""
        select rc from ReportingCalculation rc
                left join fetch rc.order o 
                left join fetch rc.userBalanceAction uba
        order by rc.date asc
        """)
    List<ReportingCalculation> findAllOrdByDate();

    @Modifying
    @Query("delete from ReportingCalculation rc where rc.order.userInstrument.id = :instrumentId")
    void deleteAllOperationsByInstrumentId(Long instrumentId);

    @Modifying
    @Query("delete from ReportingCalculation rc where rc.userBalanceAction.userInstrument.id = :instrumentId")
    void deleteAllDepositsByInstrumentId(Long instrumentId);

    @Query("select rc.balance from ReportingCalculation rc where rc.userInstrument.id = :userInstrumentId order by rc.date desc")
    BigDecimal getBalanceForTheUserInstrument(Long userInstrumentId, Limit limit);
}
