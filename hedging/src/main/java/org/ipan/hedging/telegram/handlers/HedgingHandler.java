package org.ipan.hedging.telegram.handlers;

import jakarta.transaction.Transactional;
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
import org.ipan.hedging.telegram.statetransitions.UpdateType;
import org.ipan.hedging.telegram.views.BalanceViews;
import org.ipan.hedging.telegram.views.HedgeViews;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;

@TransitionHandler
@AllArgsConstructor
@Slf4j
public class HedgingHandler {
    private final UserRepo userRepo;
    private final TelegramState telegramState;
    private final BalanceViews balanceViews;
    private final HedgeViews hedgeViews;

    @MatchStates({
            @MatchState(state = States.MAIN_MENU, callbackData = InlineMenuCallbacks.MENU_START_HEDGE),
            @MatchState(state = States.HEDGE_PREVIEW, callbackData = InlineMenuCallbacks.HEDGE_CHANGE_ORDER)
    })
    @Transactional
    public void start_hedge(UserState userState, Update update) {
        AppUser user = userRepo.findById(userState.getTelegramId()).get();
        if (!user.hasAccount()) {
            log.warn("user has no account: {}", user.getTelegramUsername());
            balanceViews.noAccount(userState, user);
        }

        hedgeViews.promptInstruments(userState, new ArrayList<>(user.getAvailableInstruments()));
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_INSTRUMENT));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_INSTRUMENT, updateTypes = UpdateType.CALLBACK_QUERY),
    })
    public void handleInstrument(UserState userState, Update update) {
        AppUser user = userRepo.findById(userState.getTelegramId()).get();

        // TODO: save instrument

        // TODO: mt5 get symbol info

        // TODO: check if can hedge out then

        hedgeViews.showOperations(userState);
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_OPERATION));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_OPERATION, updateTypes = UpdateType.CALLBACK_QUERY),
    })
    public void handleOperation(UserState userState, Update update) {
        AppUser user = userRepo.findById(userState.getTelegramId()).get();

        // TODO: mt5 get symbol info
        // TODO: check if can hedge out then

        hedgeViews.promptVolume(userState);
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_VOLUME));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_VOLUME, updateTypes = UpdateType.MESSAGE),
    })
    public void handleVolume(UserState userState, Update update) {
        AppUser user = userRepo.findById(userState.getTelegramId()).get();

        // TODO: mt5 get symbol info
        // TODO: check if can hedge out then
        hedgeViews.showPreview(userState);
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_VOLUME));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_PREVIEW, callbackData = InlineMenuCallbacks.HEDGE_PLACE_ORDER),
    })
    public void handlePreviewPlace(UserState userState, Update update) {
        AppUser user = userRepo.findById(userState.getTelegramId()).get();

        log.info("User place order...");
        // TODO: mt5 get symbol info
        // TODO: check if can hedge out then
        // todo: show previwe
    }
}
