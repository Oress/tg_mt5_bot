package org.ipan.hedging.domain.service;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.Instrument;

import java.math.BigDecimal;

@Data
@Builder
@Value
public class VolumeCalculationParams {
    Long userId;
    Boolean isBuy;
    Integer userAmount;
    Double tradeContractSize;
    Instrument instrument;
    Mt5ServiceImpl.InfoTick tick;
    AppUserInstrument userInstrument;
}
