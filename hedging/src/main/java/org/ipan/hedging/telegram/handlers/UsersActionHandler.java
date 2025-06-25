package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.projections.EnabledInstrumentPerUser;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.UserService;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserEdit;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.MatchState;
import org.ipan.hedging.telegram.statetransitions.TransitionHandler;
import org.ipan.hedging.telegram.statetransitions.UpdateType;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.ipan.hedging.telegram.views.ManageUserActionsView;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@TransitionHandler
@Slf4j
@AllArgsConstructor
public class UsersActionHandler {
    private final TelegramState telegramState;
    private final UserRepo userRepo;
    private final UserService userService;
    private final ManageUserActionsView manageUserActionsView;
    private final Mt5Service mt5Service;

    @MatchState(state = States.ADMIN_MANAGE_USERS_ACTION_PREVIEW, callbackData = InlineMenuCallbacks.MANAGE_USER_ACTION_CHANGE_INSTRUMENTS)
    public void changeEnabledInstruments(UserState userState, Update update) {
        List<EnabledInstrumentPerUser> enabledInstruments = userRepo.getEnabledInstruments(userState.getTelegramId());
        manageUserActionsView.showEnabledInstruments(userState, enabledInstruments);
        telegramState.updateUserState(userState.getTelegramId(),
                userState.withState(States.ADMIN_MANAGE_USER_ACTION_CHANGE_INSTUMENTS));
    }

    @MatchState(state = States.ADMIN_MANAGE_USER_ACTION_CHANGE_INSTUMENTS, updateTypes = UpdateType.CALLBACK_QUERY)
    public void toggleEnabledInstruments(UserState userState, Update update) {
        String data = update.getCallbackQuery().getData();
        var toggleUserInstrument = InlineMenuCallbacks.getToggleUserInstrument(data);
        if (toggleUserInstrument != null) {

            userService.toggleInstrument(toggleUserInstrument.getUserId(), toggleUserInstrument.getInstrument());
            changeEnabledInstruments(userState, update);
        }
    }

    @MatchState(state = States.ADMIN_MANAGE_USERS_ACTION_PREVIEW, callbackData = InlineMenuCallbacks.MANAGE_USER_ACTION_CHANGE_GERCHIK_CREDS)
    public void changeGerchikAccount(UserState userState, Update update) {
        manageUserActionsView.promptAccountCreds(userState);
        telegramState.updateUserState(userState.getTelegramId(),
                userState.withState(States.ADMIN_MANAGE_USER_ACTION_PROMPT_CREDS));
    }

    @MatchState(state = States.ADMIN_MANAGE_USER_ACTION_PROMPT_CREDS, updateTypes = UpdateType.MESSAGE)
    public void changeGerchikAccount_handleCreds(UserState userState, Update update) {
        String credsStr = update.getMessage().getText();
        String[] creds = credsStr.split(" ");

        try {
            if (creds.length != 2 || creds[0].isBlank() || creds[1].isBlank()) {
                log.warn("Incorrect credentials");
            }

            int accountNumber = Integer.parseInt(creds[0]);
            String accountPwd = creds[1];

            List<AppUser> users = userRepo.findByMt5AccNumber(accountNumber);
            if (!users.isEmpty()) {
                log.warn("User with the same mt5 account # already exist {}", FormattingTools.formatUser(users.get(0)));
                // TODO: show error
                return;
            }

            if (!mt5Service.validateCredentials(accountNumber, accountPwd)) {
                // TODO: log, error
                return;
            }

            userService.changeGerchikCreds(userState.getTelegramId(), accountNumber, accountPwd);
            manageUserActionsView.changeCredsSuccess(userState);
            telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.ADMIN_MANAGE_USER_ACTION_CHANGE_CREDS_SUCCESS));
        } catch (NumberFormatException e) {
            // TODO: log, error,
        }
    }

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
