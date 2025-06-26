package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.domain.model.AccountType;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.projections.EnabledInstrumentPerUser;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.UserService;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.TelegramMessages;
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
    private final TelegramMessages telegramMessages;

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
        telegramMessages.deleteMessage(update.getMessage());

        String credsStr = update.getMessage().getText();
        String[] creds = credsStr.split(" ");

        try {
            if (creds.length != 2 || creds[0].isBlank() || creds[1].isBlank()) {
                log.warn("Incorrect credentials");
            }

            int accountNumber = Integer.parseInt(creds[0]);
            String accountPwd = creds[1];

            manageUserActionsView.credsVerifyInProgress(userState);

            List<AppUser> users = userRepo.findByMt5AccNumber(accountNumber);
            if (!users.isEmpty()) {
                log.warn("User with the same mt5 account # already exist {}", FormattingTools.formatUser(users.get(0)));
                manageUserActionsView.userWithTheSameAccAlreadyExists(userState, FormattingTools.formatUser(users.get(0)));
                return;
            }

            if (!mt5Service.checkCredentials(accountNumber, accountPwd)) {
                log.warn("User enters invalid credentials {}", credsStr);
                manageUserActionsView.invalidCredentials(userState);
                return;
            }
            AccountType accountType = mt5Service.determineAccountType(accountNumber, accountPwd);
            UserEdit openedUser = telegramState.getOrCreateUserEdit(userState.getTelegramId());

            userService.changeGerchikCreds(openedUser.getSelectedUserId(), accountNumber, accountPwd, accountType);
            manageUserActionsView.changeCredsSuccess(userState);
            telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.ADMIN_MANAGE_USER_ACTION_CHANGE_CREDS_SUCCESS));
        } catch (NumberFormatException e) {
            log.error("User enters invalid credentials {}", credsStr, e);
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
