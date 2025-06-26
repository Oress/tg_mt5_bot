package org.ipan.hedging.telegram.state;


public interface TelegramState {
    UserState getOrCreateUserState(Long userId);
    UserState updateUserState(Long userId, UserState userState);

    UserEdit getOrCreateUserEdit(Long userId);
    UserEdit updateUserEdit(Long userId, UserEdit userEdit);
    UserEdit removeUserEdit(Long userId); // TODO: cleanup after finishing workflow

    UserSearchState getOrCreateUserSearchState(Long userId);
    UserSearchState updateUserSearchState(Long userId, UserSearchState userSearchState);
    UserSearchState removeUserSearchState(Long userId); // TODO: cleanup after finishing workflow

    HedgeOpState getOrCreateHedgeOp(Long userId);
    HedgeOpState updateHedgeOp(Long userId, HedgeOpState hedgeOpState);
}
