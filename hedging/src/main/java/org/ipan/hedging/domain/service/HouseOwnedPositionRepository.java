package org.ipan.hedging.domain.service;

import org.ipan.hedging.domain.model.HouseOwnedPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface HouseOwnedPositionRepository extends JpaRepository<HouseOwnedPosition, Long> {

    @Query("""
        select hp from HouseOwnedPosition hp
                join fetch hp.order o
                join o.userInstrument ui
        where hp.closedAt is null and ui.id = :userInstrumentId
        order by o.createdAt
        """)
    List<HouseOwnedPosition> findAllForClosingFifo(Long userInstrumentId);
}
