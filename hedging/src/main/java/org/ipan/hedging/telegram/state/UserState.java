package org.ipan.hedging.telegram.state;

import org.ipan.hedging.domain.model.UserRole;
import org.ipan.hedging.telegram.States;

import java.util.List;
import java.util.Locale;

public interface UserState {
    Long getTelegramId();
    States getState();
    Long getChatId();
    Integer getMenuMessageId();
    UserRole getRole();
    List<Integer> getMessagesToDelete();
    String getLanguageCode();

    UserState withTelegramId(Long value);
    UserState withState(States value);
    UserState withChatId(Long value);
    UserState withMenuMessageId(Integer value);
    UserState withRole(UserRole value);
    UserState withMessagesToDelete(List<Integer> value);
    UserState withLanguageCode(String value);

    default Locale getLocaleOrDefault() {
        String code = getLanguageCode();
        if (code == null) {
            code = "ru";
        }
        return Locale.of(code);
    }

}
