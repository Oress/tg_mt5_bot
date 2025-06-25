package org.ipan.hedging.telegram;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InlineMenuCallbacks {
    private final static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static final String TO_MAIN_MENU = "action_main_menu";
    public static final String GO_BACK = "action_go_back";
    public static final String CHANGE_LANGUAGE = "change_language";
    public static final String NTFN_OK = "notification_ok";

    public static final String CONFIRM_YES = "confirm_yes";
    public static final String CONFIRM_NO = "confirm_no";

    public static final String SETTINGS = "settings";
    public static final String HISTORY = "history";

    public static final String ADMIN_MENU = "admin_menu";

    public static final String MANAGE_USERS = "manage_users";
    public static final String MANAGE_USERS_SEARCH_RESET = "manage_users_search_reset";
    public static final String MANAGE_USERS_PREV_PAGE = "manage_users_search_prev_page";
    public static final String MANAGE_USERS_NEXT_PAGE = "manage_users_search_next_page";
    public static final String MANAGE_USER_ACTION_DEACTIVATE = "manage_user_action_deactivate";
    public static final String MANAGE_USER_ACTION_ADJUST_BALANCE_MANUALLY = "manage_user_action_adjust_balance_manually";
    public static final String MANAGE_USER_ACTION_CHANGE_TARIFF = "manage_user_action_change_tariff";
    public static final String MANAGE_USER_ACTION_CHANGE_REF_PROGRAM = "manage_user_action_change_ref_program";

    // menu actions
    public static final String MENU_SHOW_BALANCE = "menu_show_balance";
}
