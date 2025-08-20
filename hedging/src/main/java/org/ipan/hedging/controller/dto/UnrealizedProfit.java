package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnrealizedProfit {
    private BigDecimal rate;
    private Double profit;
    private Double profitHouse;
    private String instrument;
}