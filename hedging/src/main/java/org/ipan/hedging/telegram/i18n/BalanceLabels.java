package org.ipan.hedging.telegram.i18n;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@AllArgsConstructor
public class BalanceLabels {
    private final MessageSource messageSource;
    private final TgUserLocaleProvider tgUserLocaleProvider;

    private String getLocalizedMessage(String key, Object... args) {
        Locale userLocale = tgUserLocaleProvider.getUserLocale();
        return messageSource.getMessage(key, args, userLocale);
    }

    private String getLocalizedMessage(String key, Locale userLocale, Object... args) {
        return messageSource.getMessage(key, args, userLocale);
    }


    public String promptInstruments() {
        return "Please select an instrument from the list below:";
    }

    public @NonNull String promptOperation() {
        return "Please select an operation from the list below:";
    }

    public @NonNull String promptVolume() {
        return "Volume:";
    }

    public @NonNull String preview() {
        return "Preview:";
    }

    public @NonNull String placeOrder() {
        return "Place order";
    }

    public @NonNull String changeOrder() {
        return "Change order";
    }

    public @NonNull String noAccount() {
        return "You don't have access, please contact the admin.";
    }
}
