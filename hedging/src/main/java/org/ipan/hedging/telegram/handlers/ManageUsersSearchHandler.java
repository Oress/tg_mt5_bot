package org.ipan.hedging.telegram.handlers;

import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUser_;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.TelegramMessages;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserEdit;
import org.ipan.hedging.telegram.state.UserSearchState;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.MatchState;
import org.ipan.hedging.telegram.statetransitions.MatchStates;
import org.ipan.hedging.telegram.statetransitions.TransitionHandler;
import org.ipan.hedging.telegram.statetransitions.UpdateType;
import org.ipan.hedging.telegram.utils.SearchingUtils;
import org.ipan.hedging.telegram.views.ManageUserActionsView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import lombok.extern.slf4j.Slf4j;

@TransitionHandler
@Slf4j
@AllArgsConstructor
public class ManageUsersSearchHandler {
    private final int pageSize = 20;
    private final TelegramState telegramState;
    private final TelegramMessages telegramMessages;
    private final UserRepo appUserRepo;
    private final ManageUserActionsView manageUserActionsView;

    @MatchStates({
        @MatchState(forAdmin = true, state = States.ADMIN_MENU, callbackData = InlineMenuCallbacks.MANAGE_USERS),
        @MatchState(forAdmin = true, state = States.ADMIN_MANAGE_USERS, callbackData = InlineMenuCallbacks.MANAGE_USERS_SEARCH_RESET),
        @MatchState(forAdmin = true, state = States.ADMIN_MANAGE_USERS_ACTION_PREVIEW, callbackData = InlineMenuCallbacks.GO_BACK),
    })
    public void resetUserSearch(UserState userState, Update update) {
        UserSearchState searchState = telegramState.getOrCreateUserSearchState(userState.getTelegramId());
        telegramState.updateUserSearchState(userState.getTelegramId(), searchState.withCurrentPage(0).withQuery(""));

        Page<AppUser> firstPage = appUserRepo.findAll(PageRequest.of(0, pageSize).withSort(Sort.by(AppUser_.TELEGRAM_USERNAME, AppUser_.TELEGRAM_FIRST_NAME, AppUser_.TELEGRAM_ID)));
        manageUserActionsView.updMenuToManageUsersSearchResult(firstPage, userState);
        telegramState.updateUserState(userState.getTelegramId(),
                userState.withState(States.ADMIN_MANAGE_USERS));
    }


    @MatchStates({
        @MatchState(state = States.ADMIN_MANAGE_USERS, callbackData = InlineMenuCallbacks.MANAGE_USERS_NEXT_PAGE)
    })
    public void nextPage(UserState userState, Update update) {
        UserSearchState searchState = telegramState.getOrCreateUserSearchState(userState.getTelegramId());
        int pageNumber = searchState.getCurrentPage() + 1;
        String queryStr = searchState.getQuery();
        telegramState.updateUserSearchState(userState.getTelegramId(), searchState.withCurrentPage(pageNumber));
        Page<AppUser> nextPage = appUserRepo.searchByUsernameAndFirstname(
            SearchingUtils.paramterContains(queryStr),
            SearchingUtils.paramterContains(queryStr),
            PageRequest.of(pageNumber, pageSize));
        manageUserActionsView.updMenuToManageUsersSearchResult(nextPage, userState);
    }

    @MatchStates({
        @MatchState(state = States.ADMIN_MANAGE_USERS, callbackData = InlineMenuCallbacks.MANAGE_USERS_PREV_PAGE)
    })
    public void prevPage(UserState userState, Update update) {
        UserSearchState searchState = telegramState.getOrCreateUserSearchState(userState.getTelegramId());
        int pageNumber = searchState.getCurrentPage() - 1;
        String queryStr = searchState.getQuery();
        telegramState.updateUserSearchState(userState.getTelegramId(), searchState.withCurrentPage(pageNumber));
        Page<AppUser> prevPage = appUserRepo.searchByUsernameAndFirstname(
            SearchingUtils.paramterContains(queryStr),
            SearchingUtils.paramterContains(queryStr),
             PageRequest.of(pageNumber, pageSize));
        manageUserActionsView.updMenuToManageUsersSearchResult(prevPage, userState);
    }


    @MatchStates({
        @MatchState(forAdmin = true, state = States.ADMIN_MANAGE_USERS, updateTypes = UpdateType.CALLBACK_QUERY),
        @MatchState(forAdmin = true, state = States.ADMIN_MANAGE_USER_ACTION_DEACTIVATE_CONFIRM, callbackData = InlineMenuCallbacks.CONFIRM_NO),
    })
    public void openUserByCallback(UserState userState, Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String data = callbackQuery.getData();
        UserEdit userEdit = telegramState.getOrCreateUserEdit(userState.getTelegramId());

        if (data.startsWith(ManageUserActionsView.OPEN_BALANCE)) {
            String telegramIdStr = data.split(ManageUserActionsView.OPEN_BALANCE)[1];
            Long telegramId = Long.parseLong(telegramIdStr);
            openUser(userState, telegramId);
        } else if (userEdit.getSelectedUserId() != null && userState.getState() != States.ADMIN_MANAGE_USERS) {
            Long userId = userEdit.getSelectedUserId();
            openUser(userState, userId);
        }
    }

    @MatchState(forAdmin = true, state = States.ADMIN_MANAGE_USERS, updateTypes = UpdateType.MESSAGE)
    public void searchUsersByUsername(UserState userState, Update update) {
        Message message = update.getMessage();
        if (message.hasText()) {
            log.info("Searching for users with username: {}", message.getText());
            String queryStr = message.getText();
            telegramMessages.deleteMessage(message);

            UserSearchState searchState = telegramState.getOrCreateUserSearchState(userState.getTelegramId());
            telegramState.updateUserSearchState(userState.getTelegramId(), searchState.withQuery(queryStr));
            Page<AppUser> firstPage = appUserRepo.searchByUsernameAndFirstname(
                    SearchingUtils.paramterContains(queryStr),
                    SearchingUtils.paramterContains(queryStr),
                    PageRequest.of(0, pageSize));
            manageUserActionsView.updMenuToManageUsersSearchResult(firstPage, userState);
        }
    }

    public void openUser(UserState userState, Long telegramId) {
        Optional<AppUser> appUser = appUserRepo.findById(telegramId);
        if (appUser.isPresent()) {
            AppUser user = appUser.get();

            manageUserActionsView.updMenuToManageUserActionsMenu(userState, user);
            UserEdit userEdit = telegramState.getOrCreateUserEdit(userState.getTelegramId());
            telegramState.updateUserEdit(userState.getTelegramId(), userEdit.withSelectedUserId(telegramId));
            telegramState.updateUserState(userState.getTelegramId(),
                    userState.withState(States.ADMIN_MANAGE_USERS_ACTION_PREVIEW));
        } else {
            log.error("Group balance not found for ID: {}", telegramId);
        }
    }
}
