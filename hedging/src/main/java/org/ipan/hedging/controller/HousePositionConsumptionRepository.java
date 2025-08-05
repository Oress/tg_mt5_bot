package org.ipan.hedging.controller;

import org.ipan.hedging.domain.model.HousePositionConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

interface HousePositionConsumptionRepository extends JpaRepository<HousePositionConsumption, Long> {

    @Query("select sum(hpc.pnl) from HousePositionConsumption hpc where hpc.parentOrder.userInstrument.id = :userInstrumentId")
    BigDecimal getPnlForUserInstrument(Long userInstrumentId);

}
