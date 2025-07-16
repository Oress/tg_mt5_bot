package org.ipan.hedging.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTelegramActivityDto {
    private Long id;
    private Long userId;
    private String type;
    private String data;
    private String currentUserState;
    private Instant createdAt;
}