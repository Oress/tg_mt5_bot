package org.ipan.hedging.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private Long telegramId;
    private String username;
    private String firstName;
    private String accountType;
    private Integer mt5Account;
    private String googleSheetId;
    private BigDecimal coverage;
    private BigDecimal balance;
    private List<UserInstrumentDto> instruments = new ArrayList<>();
}