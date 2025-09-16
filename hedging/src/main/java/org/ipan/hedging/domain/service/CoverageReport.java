package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public class CoverageReport {
    private final CoverageRisk risk;
    private final BigDecimal coverage;
}
