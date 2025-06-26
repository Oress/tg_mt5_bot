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

    public String menuManageUsers() {
        return getLocalizedMessage("admin.menu_manage_users");
    }
}
