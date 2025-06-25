package org.ipan.hedging.telegram.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ManageUserLabels {
    private final MessageSource messageSource;
    private final TgUserLocaleProvider tgUserLocaleProvider;

    private String getLocalizedMessage(String key, Object... args) {
        Locale userLocale = tgUserLocaleProvider.getUserLocale();
        return messageSource.getMessage(key, args, userLocale);
    }

    public String preview(String telegramId, String valOrDash, String valOrDash2, String tariffLabel) {
        return getLocalizedMessage("manage_users.preview", telegramId, valOrDash, valOrDash2, tariffLabel);
    }

    public String menuChangeTariff() {
        return getLocalizedMessage("manage_users.menu.change_tariff");
    }

    public String menuChangeRefProgram() {
        return getLocalizedMessage("manage_users.menu.change_ref_program");
    }

    public String menuChangeBalance() {
        return getLocalizedMessage("manage_users.menu.change_balance");
    }

    public String menuDeactivate() {
        return getLocalizedMessage("manage_users.menu.deactivate");
    }

    public String deactivateConfirm() {
        return getLocalizedMessage("manage_users.deactivate.confirm");
    }

    public String deactivateSuccess() {
        return getLocalizedMessage("manage_users.deactivate.success");
    }

    public String changeBalancePromptAmount() {
        return getLocalizedMessage("manage_users.change_balance.prompt_amount");
    }

    public String changeRefProgramSuccess() {
        return getLocalizedMessage("manage_users.change_ref_program.success");
    }

    public String changeBalanceNegative() {
        return getLocalizedMessage("manage_users.change_balance.negative");
    }

    public String changeBalanceSuccess() {
        return getLocalizedMessage("manage_users.change_balance.success");
    }

    public String changeTariffSuccess() {
        return getLocalizedMessage("manage_users.change_tariff.success");
    }
}
