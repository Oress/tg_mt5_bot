package org.ipan.hedging.telegram.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.service.commands.TgUserId;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class FormattingTools {
    private static final DecimalFormat df = new DecimalFormat("# ###.##");

    private final CommonLabels commonLabels;

    public static String valOrDash(String val) {
        return val == null ? "-": val;
    }

    public static String valOrDash(Object val) {
        return val == null ? "-": val.toString();
    }

    public String formatUserForSearch(Long id, String login, String name) {
        String loginStr = login != null ? commonLabels.userLogin(login) : "";
        String nameStr = name != null ? commonLabels.userLogin(name) : "";
        String idStr = "ID: %s".formatted(id);
        return List.of(idStr, loginStr, nameStr).stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
    }

    public String formatUserForSearch(AppUser user) {
        if (user == null) {
            return "-";
        }
        String login = user.getTelegramUsername() != null ? commonLabels.userLogin(user.getTelegramUsername()) : "";
        String name = user.getTelegramFirstName() != null ? commonLabels.userName(user.getTelegramFirstName()) : "";
        String id = "ID: %s".formatted(user.getTelegramId());
        return List.of(login, name, id).stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
    }

    public static String formatUserLink(TgUserId user) {
        if (user == null) {
            return "-";
        }
        if (user.getUsername() != null) {
            return String.format("[@%s](https://t.me/%s)", user.getUsername(), user.getUsername());
        } else {
            return String.format("%s %s", user.getId(), user.getFirstName());
        }
    }

    public static String formatUserLink(AppUser user) {
        if (user == null) {
            return "-";
        }
        return String.format("[@%s](https://t.me/%s) %s", user.getTelegramUsername(), user.getTelegramUsername(), user.getTelegramFirstName());
    }

    public static String formatUser(AppUser user) {
        if (user == null) {
            return "-";
        }
        return String.format("%s %s", user.getTelegramUsername(), user.getTelegramFirstName());
    }

    public static String formatDateToUtc(Instant date) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC)
            .format(date);
    }


    public static String formatDateToUtc(Timestamp date) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC)
            .format(LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.systemDefault()));
    }

    public static String formatNumber(Long number) {
        return df.format(number);
    }

    public static String formatNumber(BigDecimal number) {
        return df.format(number.setScale(2, RoundingMode.HALF_DOWN));
    }

    public static String formatNumber(Integer number) {
        return df.format(number);
    }

    public static String formatPrice(BigDecimal number) {
        NumberFormat defaultCurrencyFormat = NumberFormat.getNumberInstance(Locale.of("ru"));
        return defaultCurrencyFormat.format(number);
    }

    public static String formatPrice(Integer number) {
        return formatPrice(BigDecimal.valueOf(number));
    }
}
