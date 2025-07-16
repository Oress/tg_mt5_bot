package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderConsumptionDto {
    private Long id;
    private OrderDto consumedOrder;
    private BigDecimal consumedQuoteAmount;
    private BigDecimal baseEquivalent;
}