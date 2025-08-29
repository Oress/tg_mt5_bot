package org.ipan.hedging.telegram.i18n;

import java.util.Locale;

import lombok.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class CommonLabels {
    private final MessageSource messageSource;
    private final TgUserLocaleProvider tgUserLocaleProvider;

    private String getLocalizedMessage(String key, Object... args) {
        Locale userLocale = tgUserLocaleProvider.getUserLocale();
        return messageSource.getMessage(key, args, userLocale);
    }

    private String getLocalizedMessage(String key, Locale userLocale, Object... args) {
        return messageSource.getMessage(key, args, userLocale);
    }

    public String check() {
        return getLocalizedMessage("common.check");
    }

    public String cross() {
        return getLocalizedMessage("common.cross");
    }

    public String getMainWelcome(String balances) {
        return getLocalizedMessage("main.welcome", balances);
    }

    public String yes() {
        return getLocalizedMessage("common.yes");
    }

    public String no() {
        return getLocalizedMessage("common.no");
    }

    public String manageAdd() {
        return getLocalizedMessage("common.add_new");
    }

    // SEARCH START
    public String manageSearch() {
        return getLocalizedMessage("common.search");
    }

    public String searchNoResults() {
        return getLocalizedMessage("common.search_no_results");
    }

    public String searchResults() {
        return getLocalizedMessage("common.search_results");
    }

    public String searchNextPage() {
        return getLocalizedMessage("common.search_next_page");
    }

    public String searchPrevPage() {
        return getLocalizedMessage("common.search_prev_page");
    }

    public String searchReset() {
        return getLocalizedMessage("common.search_reset");
    }
    // SEARCH END
    
    public String toMainMenu() {
        return getLocalizedMessage("common.to_main_menu");
    }

    public String goBack() {
        return getLocalizedMessage("common.go_back");
    }

    // MAIN MENU START
    public String getMenuAdminMenu() {
        return getLocalizedMessage("menu.admin_menu");
    }

    public String menuSettings() {
        return getLocalizedMessage("menu.settings");
    }

    public String menuHedge() {
        return getLocalizedMessage("menu.hedge");
    }

    // END MAIN MENU

    public String settingsChangeLanguage() {
        return getLocalizedMessage("settings.change_language");
    }

    public String settingsDescription() {
        return getLocalizedMessage("settings.description");
    }

    public String somethingWentWrong() {
        return getLocalizedMessage("common.something_went_wrong");
    }

    public String userLogin(String login) {
        return getLocalizedMessage("user_format.login", login);
    }

    public String userName(String name) {
        return getLocalizedMessage("user_format.name", name);
    }

    public String greenCircle() {
        return "🟢";
    }

    public String redCircle() {
        return "🔴";
    }

    public  String menuBalance() {
        return "⚖️ Баланс";
    }
}
