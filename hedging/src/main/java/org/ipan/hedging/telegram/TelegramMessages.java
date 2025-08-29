package org.ipan.hedging.telegram;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.UserRole;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.utils.FormattingTools;
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
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@AllArgsConstructor
@Profile("!test")
public class TelegramMessages {
    private final TelegramClient tgClient;
    private final CommonLabels commonLabels;

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
                .text(getMainMenuMessage(payload))
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
                .text(getMainMenuMessage(payload))
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
                .text(getMainMenuMessage(payload))
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
                .text(getMainMenuMessage(payload))
                .parseMode("MARKDOWN")
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .replyMarkup(getMainMenuReplyMarkup(true))
                .build();
        tgClient.execute(message);
    }

    private String getMainMenuMessage(MainMenuPayload payload) {
        List<String> balances = new ArrayList<>();
        balances.add("Общий баланс: \n %s".formatted(FormattingTools.formatPrice(payload.deposits)));

        if (payload.cadBalance != null) {
            balances.add("Баланс по USD/CAD: \n %s".formatted(FormattingTools.formatPrice(payload.cadBalance)));
        }

        if (payload.eurBalance != null) {
            balances.add("Баланс по EUR/USD: \n %s".formatted(FormattingTools.formatPrice(payload.eurBalance)));
        }

        String balancesStr = String.join("\n\n", balances);

        return commonLabels.getMainWelcome(balancesStr);
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
        private BigDecimal deposits = BigDecimal.ZERO;
        private BigDecimal cadBalance;
        private BigDecimal eurBalance;
    }
}
