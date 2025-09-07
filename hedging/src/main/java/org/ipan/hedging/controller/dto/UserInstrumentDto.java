package org.ipan.hedging.controller.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInstrumentDto {
    private Long id;
    private String label;
    private String mode;
    private String base;
    private String quote;
    private BigDecimal commissionPercentage = BigDecimal.ZERO;
    private BigDecimal swap = BigDecimal.ZERO;
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal clientOwnedBaseAmount = BigDecimal.ZERO;
    private BigDecimal houseOwnedBaseAmount = BigDecimal.ZERO;
    private BigDecimal clientOwnedQuoteAmount = BigDecimal.ZERO;
}