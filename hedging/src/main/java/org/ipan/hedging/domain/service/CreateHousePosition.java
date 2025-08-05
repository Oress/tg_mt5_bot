package org.ipan.hedging.domain.service;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.math.BigDecimal;

@Data
@Builder
@Value
public class CreateHousePosition {
    BigDecimal amount;
}
