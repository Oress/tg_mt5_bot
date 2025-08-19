package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerminalPositionDto {
//    private String external_id;
    private Long identifier;
    private Double priceCurrent;
    private Double priceOpen;
    private Double profit;
//    private Integer reason;
//    private Double sl;
//    private Double swap;
    private String symbol;
    private Long ticket;
    private Long time;
//    private Long time_msc;
//    private Long time_update;
//    private Long time_update_msc;
//    private Double tp;
    private Integer type;
    private Double volume;
}