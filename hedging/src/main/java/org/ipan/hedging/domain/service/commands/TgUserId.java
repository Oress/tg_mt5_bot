package org.ipan.hedging.domain.service.commands;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TgUserId {
    private Long id;
    private String username;
    private String firstName;
}
