package org.ipan.hedging.domain.service.commands.users;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateUserCommand {
    private Long telegramId;
    private String username;
    private String firstName;
    private String languageCode;
    private Long tariffId;
    private String refferalLink;
}
