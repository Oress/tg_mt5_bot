package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private UUID id;
    private List<OrderConsumptionDto> consumptions = new ArrayList<>();
    private Long deal;
    private Long order;
    private String type; // OrderType
    private String status; // OrderStatus
    private Integer mt5AccNumber;
    private BigDecimal price;
    private Instant createdAt;

    private BigDecimal userBaseAmount;
    private BigDecimal userEnteredQuoteAmount;
    private BigDecimal executedVolume;
    private BigDecimal executedBaseAmount;
    private BigDecimal quoteAmountLeftToUnhedge;
    private BigDecimal houseBaseAmountFrom;
    private BigDecimal houseBaseAmountTo;
    private BigDecimal clientBaseAmountFrom;
    private BigDecimal clientBaseAmountTo;

}