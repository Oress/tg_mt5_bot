package org.ipan.hedging.telegram.state;

import java.math.BigDecimal;

public interface HedgeOpState {
    String getSymbol();
    String getOperation();
    BigDecimal getVolume();

    HedgeOpState withSymbol(String value);
    HedgeOpState withOperation(String value);
    HedgeOpState withVolume(BigDecimal value);
}
