package org.ipan.hedging.telegram;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InlineMenuCallbacks {
    public static final String HEDGE_PLACE_ORDER = "hedge_place_order";
    public static final String HEDGE_CHANGE_ORDER = "hedge_change_order";
    public static final String HEDGE_OP_SELL = "hedge_op_sell";
    public static final String HEDGE_OP_BUY = "hedge_op_buy";
    public static final String MANAGE_USER_ACTION_VIEW_POSITIONS = "manage_user_action_view_positions";
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
    public static final String MANAGE_USER_ACTION_CHANGE_INSTRUMENTS = "manage_user_action_change_instruments";
    public static final String MANAGE_USER_ACTION_CHANGE_GERCHIK_CREDS = "manage_user_action_change_gerchik_creds";
    public static final String SYNC_WITH_GOOGLE_SHEET = "sync_with_google_sheet";
    public static final String GENERATE_DASHBOARD_PWD = "generate_dashboard_pwd";
    public static final String EXECUTE_ORDER_AS_CLIENT = "execute_order_as_client";

    // menu actions
    public static final String MENU_SHOW_INFO = "menu_show_info";
    public static final String MENU_SHOW_BALANCE = "menu_show_balance";
    public static final String MENU_START_HEDGE = "menu_start_hedge";

    private static final String TOGGLE_USER_INSTRUMENT = "/tusinst/";
    public static String toggleInstrumentForUser(Long userId, String instrument) {
        ToggleUserInstrumentPayload payload = new ToggleUserInstrumentPayload(userId, instrument);
        return TOGGLE_USER_INSTRUMENT + gson.toJson(payload);
    }

    public static ToggleUserInstrumentPayload getToggleUserInstrument(String data) {
        ToggleUserInstrumentPayload payload = null;
        if (data.startsWith(TOGGLE_USER_INSTRUMENT)) {
            String payloadStr = data.split(TOGGLE_USER_INSTRUMENT)[1];

            try {
                payload = gson.fromJson(payloadStr, ToggleUserInstrumentPayload.class);
            } catch (Exception e) {
                log.error("Cannot extract payload for ToggleWalletSessionPayload ", e);
            }
        }
        return payload;
    }

    @AllArgsConstructor
    @Getter
    public static class ToggleUserInstrumentPayload {
        private Long userId;
        private String instrument;
    }
}
