package org.ipan.hedging.telegram.views;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.ipan.hedging.telegram.state.UserState;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@Component
@AllArgsConstructor
public class BalanceViews {
    private final TelegramClient tgClient;
    private final CommonViews commonViews;
    private final CommonLabels commonLabels;

    public void noAccount(UserState userState, AppUser user) {

    }

    public void showBalance(UserState userState, AppUser user) {

    }
}
