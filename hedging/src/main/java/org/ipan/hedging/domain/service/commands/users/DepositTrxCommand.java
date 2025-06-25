package org.ipan.hedging.domain.service.commands.users;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DepositTrxCommand {
    private Long userId;
    private BigDecimal trxAmount;
}
