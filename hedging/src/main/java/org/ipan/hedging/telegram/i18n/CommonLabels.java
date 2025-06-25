package org.ipan.hedging.telegram.i18n;

import java.util.Locale;

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

    public String getMainWelcome() {
        return getLocalizedMessage("main.welcome");
    }

    public String getCommonGroupBalance(String trx) {
        return getLocalizedMessage("common.balance.group", trx);
    }

    public String getCommonPersonalBalance(String trx) {
        return getLocalizedMessage("common.balance.personal", trx);
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
    public String getTxType1(String amount) {
        return getLocalizedMessage("menu.tx_type1", amount);
    }

    public String getTxType2(String amount) {
        return getLocalizedMessage("menu.tx_type2", amount);
    }

    public String getTxCustomAmnt(String amount) {
        return getLocalizedMessage("menu.tx_custom_amnt", amount);
    }

    public String getAutoDelegation() {
        return getLocalizedMessage("menu.auto_delegation");
    }

    public String getMenuManageGroup() {
        return getLocalizedMessage("menu.manage_group");
    }

    public String getMenuManageReferals() {
        return getLocalizedMessage("menu.manage_referals");
    }

    public String getMenuWithdraw() {
        return getLocalizedMessage("menu.withdraw_trx");
    }

    public String getMenuDeposit() {
        return getLocalizedMessage("menu.deposit");
    }

    public String getMenuAdminMenu() {
        return getLocalizedMessage("menu.admin_menu");
    }

    public String getMenuTxHistory() {
        return getLocalizedMessage("menu.transactions_history");
    }

    public String menuSettings() {
        return getLocalizedMessage("menu.settings");
    }

    public String getMenuWallets() {
        return getLocalizedMessage("menu.wallets");
    }

    // END MAIN MENU

    public String settingsTxHistory() {
        return getLocalizedMessage("settings.transactions_history");
    }

    public String settingsChangeLanguage() {
        return getLocalizedMessage("settings.change_language");
    }

    public String settingsDescription() {
        return getLocalizedMessage("settings.description");
    }

    public String settingsShowWalletsEnabled() {
        return getLocalizedMessage("settings.show_wallets_in_menu.enabled");
    }

    public String settingsShowWalletsDisabled() {
        return getLocalizedMessage("settings.show_wallets_in_menu.disabled");
    }

    public String somethingWentWrong() {
        return getLocalizedMessage("common.something_went_wrong");
    }

    public String topup(Locale loc) {
        return getLocalizedMessage("notification.topup", loc);
    }

    public String withdrawFail(Locale loc) {
        return getLocalizedMessage("notification.withdraw.fail", loc);
    }

    public String withdrawSuccess(Locale loc) {
        return getLocalizedMessage("notification.withdraw.success", loc);
    }

    public String userLogin(String login) {
        return getLocalizedMessage("user_format.login", login);
    }

    public String userName(String name) {
        return getLocalizedMessage("user_format.name", name);
    }

    public String defaultTariffWarning() {
        return getLocalizedMessage("warning.default_tariff");
    }

    public String historyWaiting() {
        return getLocalizedMessage("history.waiting");
    }

    public String historyComplete() {
        return getLocalizedMessage("history.success");
    }

    public String historyRefund() {
        return getLocalizedMessage("history.refund");
    }

    public String referalsSummary(String pendingCommission, String referalProgramDescr, String referals) {
        return getLocalizedMessage("referals.summary", pendingCommission, referalProgramDescr, referals);
    }

    public String referalPayment(Locale locale, String amount) {
        return getLocalizedMessage("referal.payment", locale, amount);
    }

    public String noReferrals() {
        return getLocalizedMessage("referal.no_referrals");
    }

    public String refProgramNotSet() {
        return getLocalizedMessage("referal.not_set");
    }

    public String greenCircle() {
        return "🟢";
    }

    public String redCircle() {
        return "🔴";
    }

}
