package org.ipan.hedging.domain.service;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@Value
public class OrderConsumption {
    UUID orderId;
    BigDecimal quoteConsumptionAmount;
}
