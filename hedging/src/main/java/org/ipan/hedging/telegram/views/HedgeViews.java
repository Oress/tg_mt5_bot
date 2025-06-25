package org.ipan.hedging.telegram.views;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.ipan.hedging.telegram.i18n.HedgeLabels;
import org.ipan.hedging.telegram.state.UserState;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Component
@AllArgsConstructor
public class HedgeViews {
    private final TelegramClient tgClient;
    private final CommonViews commonViews;
    private final CommonLabels commonLabels;
    private final HedgeLabels hedgeLabels;

    @SneakyThrows
    public void promptInstruments(UserState userState, List<Instrument> instruments) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptInstuments())
                .replyMarkup(getInstrumentsMarkup(instruments))
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void showOperations(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptOperation())
                .replyMarkup(getAdminMenuReplyMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void promptVolume(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptVolume())
                .replyMarkup(getAdminMenuReplyMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void showPreview(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.preview())
                .replyMarkup(getAdminMenuReplyMarkup())
                .build();
        tgClient.execute(message);
    }

    private InlineKeyboardMarkup getInstrumentsMarkup(List<Instrument> instruments) {
        var builder = InlineKeyboardMarkup.builder();

        for (Instrument instrument : instruments) {
            builder
                    .keyboardRow(
                            new InlineKeyboardRow(
                                    InlineKeyboardButton
                                            .builder()
                                            .text(instrument.getLabel())
                                            .callbackData(instrument.getLabel())
                                            .build()));
        }

        return builder
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.toMainMenu())
                                        .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                        .build()))
                .build();
    }


    private InlineKeyboardMarkup getAdminMenuReplyMarkup() {
        return InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(hedgeLabels.placeOrder())
                                        .callbackData(InlineMenuCallbacks.HEDGE_PLACE_ORDER)
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(hedgeLabels.changeOrder())
                                        .callbackData(InlineMenuCallbacks.HEDGE_CHANGE_ORDER)
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.toMainMenu())
                                        .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                        .build()))
                .build();
    }

}
