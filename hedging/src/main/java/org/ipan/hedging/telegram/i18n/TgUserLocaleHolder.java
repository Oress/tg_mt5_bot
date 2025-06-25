package org.ipan.hedging.telegram.i18n;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class TgUserLocaleHolder {
    private static final ThreadLocal<Locale> userLocale = new ThreadLocal<>();

    public static Locale getUserLocale() {
        return userLocale.get();
    }

    public static void setUserLocale(Locale value) {
        userLocale.set(value);
    }
}
