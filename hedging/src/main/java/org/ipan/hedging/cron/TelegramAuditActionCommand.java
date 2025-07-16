package org.ipan.hedging.cron;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class TelegramAuditActionCommand {
    Long userId;
    String type;
    String data;
    String currentUserState;
    Instant createdAt;
}
