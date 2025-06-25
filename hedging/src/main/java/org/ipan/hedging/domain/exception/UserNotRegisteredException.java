package org.ipan.hedging.domain.exception;

import java.util.List;


import lombok.Getter;
import org.ipan.hedging.domain.service.commands.TgUserId;

@Getter
public class UserNotRegisteredException extends EnergyRentException {
    private List<TgUserId> userIds;

    public UserNotRegisteredException(List<TgUserId> userIds, String message) {
        super(message);
        this.userIds = userIds;
    }

    public UserNotRegisteredException(String message, Throwable cause) {
        super(message, cause);
    }

}
