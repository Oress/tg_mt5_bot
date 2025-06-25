package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.service.UserService;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.TelegramMessages;
import org.ipan.hedging.telegram.i18n.TgUserLocaleHolder;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.MatchState;
import org.ipan.hedging.telegram.statetransitions.TransitionHandler;
import org.ipan.hedging.telegram.statetransitions.UpdateType;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Locale;

@AllArgsConstructor
@TransitionHandler
public class MainMenuHandler {
    private final TelegramState telegramState;
    private final TelegramMessages telegramMessages;
    private final UserService userService;

    @MatchState(state = States.CHOOSE_LANGUAGE, updateTypes = UpdateType.CALLBACK_QUERY)
    public void changeLanguage(UserState userState, Update update) {
        AppUser user = userService.getById(userState.getTelegramId());
        String data = update.getCallbackQuery().getData();
        
        if ("en".equals(data) || "ru".equals(data)) {
            userService.setLanguage(userState.getTelegramId(), data);

            TgUserLocaleHolder.setUserLocale(Locale.of(data));

            telegramMessages.updateUserMainMenuBasedOnRole(userState, user);
            telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.MAIN_MENU));
        }
    }
}
