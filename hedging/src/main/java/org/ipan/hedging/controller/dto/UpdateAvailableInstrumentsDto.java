package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAvailableInstrumentsDto {
    private Boolean cadUsd;
    private Boolean eurUsd;
}