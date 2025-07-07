package org.ipan.hedging.telegram.state;

public interface HedgeOpState {
    String getInstrumentLabel();
    String getOperation();
    Integer getUserAmount();

    HedgeOpState withInstrumentLabel(String value);
    HedgeOpState withOperation(String value);
    HedgeOpState withUserAmount(Integer value);
}
