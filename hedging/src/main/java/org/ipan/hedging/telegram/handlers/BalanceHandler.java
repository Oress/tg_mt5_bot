package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.MatchState;
import org.ipan.hedging.telegram.statetransitions.MatchStates;
import org.ipan.hedging.telegram.statetransitions.TransitionHandler;
import org.ipan.hedging.telegram.views.BalanceViews;
import org.telegram.telegrambots.meta.api.objects.Update;

@TransitionHandler
@AllArgsConstructor
@Slf4j
public class BalanceHandler {
    private final UserRepo userRepo;
    private final TelegramState telegramState;
    private final BalanceViews balanceViews;

    @MatchStates({
            @MatchState(state = States.MAIN_MENU, callbackData = InlineMenuCallbacks.MENU_SHOW_BALANCE),
    })
    public void showBalance(UserState userState, Update update) {
        AppUser user = userRepo.getById(userState.getTelegramId());

        if (!user.hasAccount()) {
            log.warn("user has no account: {}", user.getTelegramUsername());
            balanceViews.noAccount(userState, user);
        }

        balanceViews.showBalance(userState, user);
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.BALANCE_REVIEW));
    }
}
