package org.ipan.hedging.application.service.command;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.With;

@Data
@Builder
@Value
@With
public class PlaceOrderCommand {
    boolean isBuy;
    Long userId;
    String instrumentLabel;
    Double volume;
    Integer userAmount;
    boolean isLiquidation;
    Long ticket;
}
