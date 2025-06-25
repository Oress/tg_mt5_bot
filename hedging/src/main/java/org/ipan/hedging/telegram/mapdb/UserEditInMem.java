package org.ipan.hedging.telegram.mapdb;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.ipan.hedging.telegram.state.UserEdit;

@Value
@With
@Builder
@Jacksonized
public class UserEditInMem implements UserEdit {
    Long selectedUserId;

    public static UserEditInMem of(UserEdit prototype) {
        return UserEditInMem.builder()
                .selectedUserId(prototype.getSelectedUserId())
                .build();
    }
}
