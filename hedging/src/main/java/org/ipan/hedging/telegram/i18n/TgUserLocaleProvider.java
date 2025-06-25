package org.ipan.hedging.telegram.i18n;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
// @Scope(TelegramMsgScope.TG_MESSAGE)
public class TgUserLocaleProvider {

    public Locale getUserLocale() {
        return TgUserLocaleHolder.getUserLocale();
    }
}
