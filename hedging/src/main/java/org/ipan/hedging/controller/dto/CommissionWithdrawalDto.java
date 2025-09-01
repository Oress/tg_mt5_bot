package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommissionWithdrawalDto {
    private Long id;
    private String instrument;
    private BigDecimal amount;
    private Instant date;
    private Instant createdAt;
}