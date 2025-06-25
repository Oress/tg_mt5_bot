package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.service.UserService;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserEdit;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.MatchState;
import org.ipan.hedging.telegram.statetransitions.TransitionHandler;
import org.ipan.hedging.telegram.utils.ParseUtils;
import org.ipan.hedging.telegram.views.ManageUserActionsView;
import org.telegram.telegrambots.meta.api.objects.Update;

@TransitionHandler
@Slf4j
@AllArgsConstructor
public class UsersActionHandler {
    private final int pageSize = 20;
    private final TelegramState telegramState;
    private final UserService userService;
    private final ManageUserActionsView manageUserActionsView;
    private final ParseUtils parseUtils;

    @MatchState(state = States.ADMIN_MANAGE_USERS_ACTION_PREVIEW, callbackData = InlineMenuCallbacks.MANAGE_USER_ACTION_DEACTIVATE)
    public void startDeactivateUser(UserState userState, Update update) {
        manageUserActionsView.confirmDeactivateUserMsg(userState);
        telegramState.updateUserState(userState.getTelegramId(),
                userState.withState(States.ADMIN_MANAGE_USER_ACTION_DEACTIVATE_CONFIRM));
    }

    @MatchState(state = States.ADMIN_MANAGE_USER_ACTION_DEACTIVATE_CONFIRM, callbackData = InlineMenuCallbacks.CONFIRM_YES)
    public void confirmDeactivateUser(UserState userState, Update update) {
        UserEdit openUser = telegramState.getOrCreateUserEdit(userState.getTelegramId());
        userService.deactivateUser(openUser.getSelectedUserId());
        manageUserActionsView.userDeleted(userState);
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.ADMIN_MANAGE_USER_ACTION_DEACTIVATE_SUCCESS));
    }
}
