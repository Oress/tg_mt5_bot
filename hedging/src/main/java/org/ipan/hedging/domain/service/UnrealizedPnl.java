package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class UnrealizedPnl {
    public BigDecimal result;
}
