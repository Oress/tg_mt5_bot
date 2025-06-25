package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.MatchState;
import org.ipan.hedging.telegram.statetransitions.MatchStates;
import org.ipan.hedging.telegram.statetransitions.TransitionHandler;
import org.ipan.hedging.telegram.views.AdminViews;
import org.telegram.telegrambots.meta.api.objects.Update;

@TransitionHandler
@AllArgsConstructor
@Slf4j
public class AdminMenuHandler {
    private final TelegramState telegramState;

    private final AdminViews adminViews;

    @MatchStates({
        @MatchState(forAdmin = true, state = States.MAIN_MENU, callbackData = InlineMenuCallbacks.ADMIN_MENU),
    })
    public void handleAdminMenu(UserState userState, Update update) {
        adminViews.updMenuToAdminMenu(userState);
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.ADMIN_MENU));
    }
}