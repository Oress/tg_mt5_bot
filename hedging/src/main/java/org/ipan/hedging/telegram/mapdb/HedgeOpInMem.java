package org.ipan.hedging.telegram.mapdb;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.ipan.hedging.telegram.state.HedgeOpState;
import org.ipan.hedging.telegram.state.UserEdit;

import java.math.BigDecimal;

@Value
@With
@Builder
@Jacksonized
public class HedgeOpInMem implements HedgeOpState {
    private String symbol;
    private String operation;
    private BigDecimal volume;

    public static HedgeOpInMem of(HedgeOpState prototype) {
        return HedgeOpInMem.builder()
                .symbol(prototype.getSymbol())
                .operation(prototype.getOperation())
                .volume(prototype.getVolume())
                .build();
    }
}
