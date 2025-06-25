package org.ipan.hedging.telegram.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class AdminLabels {
    private final MessageSource messageSource;
    private final TgUserLocaleProvider tgUserLocaleProvider;

    private String getLocalizedMessage(String key, Object... args) {
        Locale userLocale = tgUserLocaleProvider.getUserLocale();
        return messageSource.getMessage(key, args, userLocale);
    }

    public String manage() {
        return getLocalizedMessage("admin.manage");
    }

    public String menuManageGroups() {
        return getLocalizedMessage("admin.menu_manage_groups");
    }

    public String menuManageUsers() {
        return getLocalizedMessage("admin.menu_manage_users");
    }

    public String menuRefPrograms() {
        return getLocalizedMessage("admin.menu_ref_programs");
    }

    public String menuItrxBalance() {
        return getLocalizedMessage("admin.menu_itrx_balance");
    }

    public String menuSweepStats() {
        return getLocalizedMessage("admin.menu_sweep_stats");
    }

    public String menuWithdrawSweep() {
        return getLocalizedMessage("admin.menu_withdraw_sweep");
    }

    public String menuTariffs() {
        return getLocalizedMessage("admin.menu_tariffs");
    }


    public String withdrawPromptWallet() {
        return getLocalizedMessage("admin.withdraw.prompt_wallet");
    }

    public String withdrawPromptAmount() {
        return getLocalizedMessage("admin.withdraw.prompt_amount");
    }

    public String withdrawNotEnough() {
        return getLocalizedMessage("admin.withdraw.not_enough");
    }

    public String withdrawInProgress() {
        return getLocalizedMessage("admin.withdraw.in_progress");
    }

    public String itrxStats(String balance, String totalNumOfOrders, String totalEnergy, String itrxFee) {
        return getLocalizedMessage("admin.itrx_stats", balance, totalNumOfOrders, totalEnergy, itrxFee);
    }

    public String sweepStats(String wallets) {
        return getLocalizedMessage("admin.sweep_stats", wallets);
    }

    public String sweepStatsItem(String address, String amnt) {
        return getLocalizedMessage("admin.sweep_stats.item", address, amnt);
    }
}
