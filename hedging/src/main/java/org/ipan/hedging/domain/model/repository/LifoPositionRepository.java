package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.LifoPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LifoPositionRepository extends JpaRepository<LifoPosition, Long> {
    // FIFO style older records are processed first.
    @Query("""
        select lp from LifoPosition lp
                join fetch lp.order o
                join o.userInstrument ui
        where lp.closedAt is null and ui.id = :userInstrumentId
        order by o.createdAt
        """)
    List<LifoPosition> findAllForUnhendgingFifo(Long userInstrumentId);
}
