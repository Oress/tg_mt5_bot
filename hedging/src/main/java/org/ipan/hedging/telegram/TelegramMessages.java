package org.ipan.hedging.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.domain.model.InstrumentMode;
import org.ipan.hedging.domain.model.UserRole;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.ipan.hedging.telegram.views.CommonViews;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@AllArgsConstructor
@Profile("!test")
public class TelegramMessages {
    private final TelegramClient tgClient;
    private final CommonLabels commonLabels;
    private final CommonViews commonViews;

    @SneakyThrows
    public void updMenuToShowInfo(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(commonLabels.menuInformationLabel())
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        try {
            tgClient.execute(message);
        } catch (Exception e) {
            log.error("Failed to updateMsgToChangeLanguage user: {}", userState, e);
        }
    }


    @SneakyThrows
    public void updMenuToShowBalance(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(commonLabels.settingsChangeLanguage())
                .replyMarkup(changeLanguageNotficationMarkup())
                .build();
        try {
            tgClient.execute(message);
        } catch (Exception e) {
            log.error("Failed to updateMsgToChangeLanguage user: {}", userState, e);
        }
    }

    public void deleteMessage(Long chatId, Integer messageId) {
        DeleteMessage deleteMessage = DeleteMessage
                .builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();
        try {
            tgClient.execute(deleteMessage);
        } catch (Exception e) {
            log.error("Failed to delete message for chat msg: {} chatid: {}", messageId, chatId, e);
        }
    }

    public void deleteMessages(Long chatId, List<Integer> messageIds) {
        DeleteMessages deleteMessages = DeleteMessages
                .builder()
                .chatId(chatId)
                .messageIds(messageIds)
                .build();
        try {
            tgClient.execute(deleteMessages);
        } catch (Exception e) {
            log.error("Failed to delete messages for chat msgs: {} chatid: {}", messageIds, chatId, e);
        }
    }

    public void deleteMessage(Message message) {
        DeleteMessage deleteMessage = DeleteMessage
                .builder()
                .chatId(message.getChatId())
                .messageId(message.getMessageId())
                .build();
        try {
            tgClient.execute(deleteMessage);
        } catch (Exception e) {
            log.error("Failed to delete message {}", message.getMessageId(), e);
        }
    }

    public void deleteMessage(UserState userState, CallbackQuery callbackQuery) {
        DeleteMessage deleteMessage = DeleteMessage
                .builder()
                .chatId(userState.getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .build();
        try {
            tgClient.execute(deleteMessage);
        } catch (Exception e) {
            log.error("Failed to delete callback message {}, userstate {}",
                    callbackQuery.getMessage().getMessageId(), userState, e);
        }
    }

    @SneakyThrows
    public Message sendUserMainMenuBasedOnRole(UserState userState, Long chatId, AppUser user, MainMenuPayload payload) {
        UserRole role = user != null ? user.getRole() : UserRole.USER;

        Message newMenuMsg = switch (role) {
            case ADMIN -> sendAdminMainMenu(userState, chatId, user, payload);
            default -> sendMainMenu(userState, chatId, user, payload);
        };
        return newMenuMsg;
    }

    @Retryable
    @SneakyThrows
    public Message sendMainMenu(UserState userState, Long chatId, AppUser user, MainMenuPayload payload) {
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .text(getMainMenuMessage(user, payload))
                .replyMarkup(getMainMenuReplyMarkup(false))
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .parseMode("MARKDOWN")
                .build();
        return tgClient.execute(message);
    }

    @Retryable
    @SneakyThrows
    public Message sendAdminMainMenu(UserState userState, Long chatId, AppUser user, MainMenuPayload payload) {
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .text(getMainMenuMessage(user, payload))
                .replyMarkup(getMainMenuReplyMarkup(true))
                .parseMode("MARKDOWN")
                .build();
        return tgClient.execute(message);
    }

    @Retryable
    @SneakyThrows
    public Message sendPromptLanguage(UserState userState, Long chatId) {
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .text("""
                        👋 Добро пожаловать!
                        Пожалуйста, выберите предпочитаемый язык, чтобы продолжить.
                        Вы всегда можете изменить язык в меню настроек позже.
                        ────────
                        👋 Welcome!
                        Please select your preferred language to continue.
                        You can always change the preferred language in settings menu later.
                        """)
                .replyMarkup(getLanguageNotficationMarkup())
                .parseMode("MARKDOWN")
                .build();
        return tgClient.execute(message);
    }

    @SneakyThrows
    public void updateUserMainMenuBasedOnRole(UserState userState, AppUser user, MainMenuPayload payload) {
        UserRole role = user != null ? user.getRole() : UserRole.USER;

        switch (role) {
            case ADMIN -> updateMsgToAdminMainMenu(userState, user, payload);
            default -> updateMsgToMainMenu(userState, user, payload);
        }
    }

    @SneakyThrows
    public void updateMsgToMainMenu(UserState userState, AppUser user, MainMenuPayload payload) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(getMainMenuMessage(user, payload))
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .parseMode("MARKDOWN")
                .replyMarkup(getMainMenuReplyMarkup(false))
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void updateMsgToAdminMainMenu(UserState userState, AppUser user, MainMenuPayload payload) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(getMainMenuMessage(user, payload))
                .parseMode("MARKDOWN")
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .replyMarkup(getMainMenuReplyMarkup(true))
                .build();
        tgClient.execute(message);
    }

    private String getMainMenuMessage(AppUser user, MainMenuPayload payload) {
        String positionsStr = user.getAvailableInstruments().stream().filter(i -> i.getActive()).map(userInstrument -> {
            Instrument instrument = userInstrument.getInstrument();

            BigDecimal hedgedAmount = null;
            if (InstrumentMode.LONG.equals(instrument.getMode())) {
                hedgedAmount = userInstrument.getClientOwnedQuoteAmount();
            } else if (InstrumentMode.SHORT.equals(instrument.getMode())) {
                hedgedAmount = userInstrument.getClientOwnedBaseAmount();
            }
            hedgedAmount = hedgedAmount.setScale(0, RoundingMode.HALF_DOWN);

            return """
                    Инструмент: %s
                    Захеджировано: *%s %s*
                    """
                    .formatted(instrument.getLabel(), FormattingTools.formatPrice(hedgedAmount), instrument.getSymbolByMode());
        }).collect(Collectors.joining("\n"));

        String statsStr = """
                        *Статистика*
                        
                        %s
                        """.formatted(positionsStr);

        List<String> balances = new ArrayList<>();
        BigDecimal commonBalance = user.getBalanceUsd();
        BigDecimal coverage = BigDecimal.ZERO;

        if (payload.cadBalance != null) {
            coverage = coverage.add(payload.cadUnrealizedPnl);
            commonBalance = commonBalance.add(payload.cadBalance);
            balances.add("Баланс по USD/CAD: \n*%s %s*".formatted(FormattingTools.formatPrice(payload.cadBalance), "USD"));
        }

        if (payload.eurBalance != null) {
            coverage = coverage.add(payload.eurUnrealizedPnl);
            commonBalance = commonBalance.add(payload.eurBalance);
            balances.add("Баланс по EUR/USD: \n*%s %s*".formatted(FormattingTools.formatPrice(payload.eurBalance), "USD"));
        }

        balances.addFirst("Общий баланс: \n*%s %s*".formatted(FormattingTools.formatPrice(commonBalance), "USD"));

        coverage = coverage.add(commonBalance);
        balances.addFirst("Средства(обеспечение): \n*%s %s*".formatted(FormattingTools.formatPrice(coverage), "USD"));

        String balancesStr = """
                        *Балансы*
                        
                        %s
                        """.formatted(String.join("\n\n", balances));

        String menuMessageStr = """
                %s
                %s
                """.formatted(statsStr, balancesStr);

        return commonLabels.getMainWelcome(menuMessageStr);
    }

    private InlineKeyboardMarkup getLanguageNotficationMarkup() {
        return InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text("Русский")
                                        .callbackData("ru")
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text("English")
                                        .callbackData("en")
                                        .build()))
                .build();
    }

    private InlineKeyboardMarkup changeLanguageNotficationMarkup() {
        return InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text("Русский")
                                        .callbackData("ru")
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text("English")
                                        .callbackData("en")
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.toMainMenu())
                                        .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.goBack())
                                        .callbackData(InlineMenuCallbacks.GO_BACK)
                                        .build()))
                .build();
    }

    private InlineKeyboardMarkup settingsMenuMarkup(Boolean showWalletsInMenuEnabled) {
        var builder = InlineKeyboardMarkup.builder();

        builder
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.settingsChangeLanguage())
                                        .callbackData(InlineMenuCallbacks.CHANGE_LANGUAGE)
                                        .build()));
        builder.keyboardRow(
                new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text(commonLabels.toMainMenu())
                                .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                .build()));
        return builder.build();
    }

    private InlineKeyboardMarkup getMainMenuReplyMarkup(Boolean isAdmin) {
        var builder = InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.menuHedge())
                                        .callbackData(InlineMenuCallbacks.MENU_START_HEDGE)
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.menuBalance())
                                        .callbackData(InlineMenuCallbacks.MENU_SHOW_BALANCE)
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.menuInformation())
                                        .callbackData(InlineMenuCallbacks.MENU_SHOW_INFO)
                                        .build()));

        if (isAdmin) {
            builder.keyboardRow(
                    new InlineKeyboardRow(
                            InlineKeyboardButton
                                    .builder()
                                    .text(commonLabels.getMenuAdminMenu())
                                    .callbackData(InlineMenuCallbacks.ADMIN_MENU)
                                    .build()));
        }

        return builder.build();
    }

    private InlineKeyboardMarkup getOkNotificationMarkup() {
        return InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text("OK")
                                        .callbackData(InlineMenuCallbacks.NTFN_OK)
                                        .build())

                )
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainMenuPayload {
        private BigDecimal cadBalance;
        private BigDecimal eurBalance;

        private BigDecimal eurUnrealizedPnl;
        private BigDecimal cadUnrealizedPnl;

    }
}
