package org.ipan.hedging.telegram.mapdb;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.ipan.hedging.telegram.state.HedgeOpState;

@Value
@With
@Builder
@Jacksonized
public class HedgeOpInMem implements HedgeOpState {
    private String instrumentLabel;
    private String operation;
    private Integer userAmount;

    public static HedgeOpInMem of(HedgeOpState prototype) {
        return HedgeOpInMem.builder()
                .instrumentLabel(prototype.getInstrumentLabel())
                .operation(prototype.getOperation())
                .userAmount(prototype.getUserAmount())
                .build();
    }
}
