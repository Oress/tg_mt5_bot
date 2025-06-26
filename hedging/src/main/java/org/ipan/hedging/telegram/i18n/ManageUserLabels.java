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

    public String preview(String telegramId, String username, String firstName,
                          String accountNumber, String accountType, String googleSheetUrl,
                          String disabled) {
        return getLocalizedMessage("manage_users.preview", telegramId, username, firstName, accountNumber, accountType, googleSheetUrl, disabled);
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
}
