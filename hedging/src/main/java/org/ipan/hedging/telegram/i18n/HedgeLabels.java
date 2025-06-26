package org.ipan.hedging.telegram.i18n;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
@AllArgsConstructor
public class HedgeLabels {
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

    public String promptOperation() {
        return "Please select an operation from the list below:";
    }

    public String promptVolumeForOut(String unit, String label, String avail) {
        return """
                *Сколько %s?* (Напишите сумму в чат📝)
                Инструмент: %s
                Доступно для покупки: %s %s
                """.formatted(unit, label, avail, unit);
    }


    public String promptVolume(String unit, String label) {
        return """
                *Сколько %s?* (Напишите сумму в чат📝)
                Инструмент: %s
                """.formatted(unit, label);
    }

    public String preview(String operation, String instrument, String amount, String unit, String rate) {
        return """
               Предварительный просмотр:

               Операция: %s
               Инструмент: %s
               Сколько: %s %s
               Приблизительный курс: %s
        """.formatted(
                operation,
                instrument,
                amount,
                unit,
                rate
        );
    }

    public String placeOrder() {
        return "Place order";
    }

    public String changeOrder() {
        return "Change order";
    }

    public String noInstruments() {
        return "You don't have instruments to use. Please contact the admin.";
    }

    public String sell() {
        return "Sell";
    }

    public String buy() {
        return "Buy";
    }

    public String minAmountWarning(Double minAmount) {
        return "The amount is less than the minimum required: " + minAmount + ". Please enter a valid amount.";
    }

    public String greaterThatAvail(BigDecimal availToSpend) {
        return "The amount exceeds your available balance: " + availToSpend + ". Please enter a valid amount.";
    }

    public String stepWarning(Double step) {
        return "The amount must be a multiple of " + step + ". Please enter a valid amount.";
    }

    public String noActivePositions() {
        return "Нет активных позиций, пожалуйста начните сначала.";
    }
}
