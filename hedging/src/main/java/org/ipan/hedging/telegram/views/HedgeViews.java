package org.ipan.hedging.telegram.views;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.ipan.hedging.telegram.i18n.HedgeLabels;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.ipan.hedging.telegram.utils.ParseUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@AllArgsConstructor
@Profile("!test")
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
                .text(hedgeLabels.promptInstruments())
                .replyMarkup(getInstrumentsMarkup(instruments))
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void showOperationsForShort(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptOperation())
                .replyMarkup(shortOperationsMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void showOperationsForLong(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptOperation())
                .replyMarkup(longOperationsMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void promptVolumeForHedge(UserState userState, String unit, String label) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptVolume(unit, label))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .parseMode("MARKDOWN")
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void promptVolumeForHedgeOut(UserState userState, String unit, String label, BigDecimal avail) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptVolumeForOut(unit, label, avail))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .parseMode("MARKDOWN")
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void promptVolumeForHedgeOutInverse(UserState userState, String unit, String label, BigDecimal avail) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.promptVolumeForOut(unit, label, avail))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .parseMode("MARKDOWN")
                .build();
        tgClient.execute(message);
    }


    @SneakyThrows
    public void showPreview(UserState userState, String operation, String instrument, BigDecimal amount, String unit, Double price) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.preview(
                        operation,
                        instrument,
                        amount,
                        unit,
                        price == null ? "N/A" : BigDecimal.valueOf(price).setScale(5, RoundingMode.HALF_DOWN).toString())
                )
                .parseMode("MARKDOWN")
                .replyMarkup(orderPreviewMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void lessThanMinAmount(UserState userState, Double minAmount) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.minAmountWarning(minAmount))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void noActivePositions(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.noActivePositions())
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void greaterThanMaxAvailable(UserState userState, BigDecimal availToSpend) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.greaterThatMax(availToSpend))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }


    @SneakyThrows
    public void greaterThanAvailable(UserState userState, BigDecimal availToSpend) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.greaterThatAvail(availToSpend))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void stepWarning(UserState userState, Double minAmount) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.stepWarning(minAmount))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void noInstruments(UserState userState, AppUser user) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.noInstruments())
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void orderFailed(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(hedgeLabels.orderFailed())
//                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void successOrder(UserState userState, String orderId, String date, String operation,
                             String instrument, BigDecimal userAmount, String price, BigDecimal totalHedged, String unit) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .parseMode("MARKDOWN")
                .text("""
                                ✅Ордер размещен
                                
                                %s
                                Статус: *исполнен*
                                Дата: %s
                                Операция: %s
                                Инструмент: %s
                                
                                Сумма: *%s*
                                Цена: *%s*
                                Захеджировано всего: *%s %s*
                                """.formatted(
                                orderId,
                        date,
                        ParseUtils.escapeMarkdown(operation),
                        instrument,
                        FormattingTools.formatPrice(userAmount),
                        price,
                        FormattingTools.formatPrice(totalHedged),
                        unit
                        )
                )
                .build();
        tgClient.execute(message);
    }

    private InlineKeyboardMarkup getInstrumentsMarkup(List<Instrument> instruments) {
        var builder = InlineKeyboardMarkup.builder();

        for (int i = 0; i < instruments.size(); i += 2) {
            List<InlineKeyboardButton> subList = instruments
                    .subList(i, Math.min(i + 2, instruments.size()))
                    .stream()
                    .map(instrument -> (InlineKeyboardButton) InlineKeyboardButton
                            .builder()
                            .text(instrument.getLabel())
                            .callbackData(instrument.getLabel())
                            .build())
                    .toList();

            builder.keyboardRow(new InlineKeyboardRow(subList));
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

    // The order of operations is intentional here. Request of the client
    private InlineKeyboardMarkup longOperationsMarkup() {
        return InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(hedgeLabels.sell())
                                        .callbackData(InlineMenuCallbacks.HEDGE_OP_BUY)
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(hedgeLabels.buy())
                                        .callbackData(InlineMenuCallbacks.HEDGE_OP_SELL)
                                        .build()
                        ))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.toMainMenu())
                                        .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                        .build()))
                .build();
    }


    private InlineKeyboardMarkup shortOperationsMarkup() {
        return InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(hedgeLabels.sell())
                                        .callbackData(InlineMenuCallbacks.HEDGE_OP_SELL)
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(hedgeLabels.buy())
                                        .callbackData(InlineMenuCallbacks.HEDGE_OP_BUY)
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


    private InlineKeyboardMarkup orderPreviewMarkup() {
        return InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(hedgeLabels.placeOrder())
                                        .callbackData(InlineMenuCallbacks.HEDGE_PLACE_ORDER)
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
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
