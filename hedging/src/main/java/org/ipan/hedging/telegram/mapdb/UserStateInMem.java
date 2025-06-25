package org.ipan.hedging.telegram.mapdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.ipan.hedging.domain.model.UserRole;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.state.UserState;

@Value
@With
@Builder
@Jacksonized
public class UserStateInMem implements UserState {
    Long telegramId;
    States state;
    Long chatId;
    Integer menuMessageId;
    UserRole role;
    List<Integer> messagesToDelete;
    String languageCode;
    Long balanceReferalProgramId;

    public static UserStateInMem of(UserState prototype) {
        return UserStateInMem.builder()
                .telegramId(prototype.getTelegramId())
                .state(prototype.getState())
                .chatId(prototype.getChatId())
                .menuMessageId(prototype.getMenuMessageId())
                .role(prototype.getRole())
                .messagesToDelete(prototype.getMessagesToDelete() == null ? Collections.emptyList() : new ArrayList<>(prototype.getMessagesToDelete()))
                .languageCode(prototype.getLanguageCode())
                .build();
    }
}
