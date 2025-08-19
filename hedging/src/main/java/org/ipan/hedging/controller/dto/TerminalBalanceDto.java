package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerminalBalanceDto {
    private Double assets;
    private Double balance;
    private Double commissionBlocked;
    private Double credit;
    private String currency;
    private Double equity;
    private Double liabilities;
    private Double margin;
    private Double marginFree;
    private Double marginInitial;
    private Double marginLevel;
    private Double marginMaintenance;
    private Double marginSoCall;
    private Double marginSoSo;
    private Double profit;
}