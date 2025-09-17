package org.ipan.hedging.telegram.i18n;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        return "Выберите инструмент:";
    }

    public String promptOperation() {
        return "Выберите операцию:";
    }

    public String promptVolumeForOut(String unit, String label, BigDecimal avail) {
        return """
                *Сколько %s?* (Напишите сумму в чат📝)
                Инструмент: %s
                Доступно для покупки: %s %s
                """.formatted(unit, label, FormattingTools.formatPrice(avail), unit);
    }

    public String promptVolume(String unit, String label) {
        return """
                *Сколько %s?* (Напишите сумму в чат📝)
                Инструмент: %s
                """.formatted(unit, label);
    }

    public String preview(String operation, String instrument, BigDecimal amount, String unit, String rate) {
        return """
                🔎 Предварительный просмотр:
                
                Операция: %s
                Инструмент: %s
                Сколько: *%s %s*
                Приблизительный курс: *%s*
                """.formatted(
                operation,
                instrument,
                FormattingTools.formatPrice(amount),
                unit,
                rate
        );
    }

    public String placeOrder() {
        return "✅ Разместить ордер";
    }

    public String changeOrder() {
        return "✏️ Изменить ордер";
    }

    public String noInstruments() {
        return "⚠️ Нет доступных инструментов для хеджирования. Пожалуйста, обратитесь к админу.";
    }

    public String sell() {
        return "\uD83D\uDCC8 Продать";
    }

    public String buy() {
        return "\uD83D\uDCC9  Купить";
    }

    public String minAmountWarning(Double minAmount) {
        return """
                ❗Число меньше минимума: %s.
                 Пожалуйста, введите корректное число."""
                .formatted(FormattingTools.formatPrice(BigDecimal.valueOf(minAmount).setScale(0, RoundingMode.HALF_DOWN)));
    }

    public String greaterThatAvail(BigDecimal availToSpend) {
        return """
                ❗Число превышает доступный баланс: %s.
                Пожалуйста, введите корректное число.
                """.formatted(FormattingTools.formatPrice(availToSpend.setScale(0, RoundingMode.HALF_DOWN)));
    }

    public String greaterThatMax(BigDecimal maxToSpend) {
        return """
                ❗Число превышает макс/ доступную сумму за 1 операцию: %s.
                Пожалуйста, введите корректное число.
                """.formatted(FormattingTools.formatPrice(maxToSpend.setScale(0, RoundingMode.HALF_DOWN)));
    }

    public String stepWarning(Double step) {
        return """
                ❗Число должно быть кратно %s.
                 Пожалуйста, введите корректное число."""
                .formatted(FormattingTools.formatPrice(BigDecimal.valueOf(step).setScale(0, RoundingMode.HALF_DOWN)));
    }

    public String noActivePositions() {
        return "Нет активных позиций, пожалуйста начните сначала.";
    }

    public String orderFailed() {
        return "❌ Не удалось выполнить ордер. Пожалуйста, попробуйте позже или обратитесь к администратору.";
    }

    public String orderTooRisky() {
        return "❌ Ордер слишком рискованный и не может быть выполнен. Ваше обеспечение составляет меньше 4% от суммы. Пожалуйста, попробуйте с меньшей суммой или пополните обеспечение.";
    }
}
