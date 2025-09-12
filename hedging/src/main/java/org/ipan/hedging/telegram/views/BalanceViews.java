package org.ipan.hedging.telegram.views;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.domain.model.InstrumentMode;
import org.ipan.hedging.telegram.i18n.BalanceLabels;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

@Component
@AllArgsConstructor
@Profile("!test")
public class BalanceViews {
    private final TelegramClient tgClient;
    private final CommonViews commonViews;
    private final CommonLabels commonLabels;
    private final BalanceLabels balanceLabels;
    private final Mt5ServiceImpl mt5Service;

    @SneakyThrows
    public void noAccount(UserState userState, AppUser user) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(balanceLabels.noAccount())
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void admin_as_client_noAccount(UserState adminUserState, AppUser user) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(adminUserState.getChatId())
                .messageId(adminUserState.getMenuMessageId())
                .text("У данного пользователя нет доступа.")
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void showBalance(UserState userState, AppUser user) {
        String positionsStr = user.getAvailableInstruments().stream().filter(i -> i.getActive()).map(userInstrument -> {
//            Mt5ServiceImpl.SymbolInfo symbolInfo = mt5Service.symbolInfo(user, tp.getSymbol());
//            BigDecimal hedgedAmount = BigDecimal.valueOf(tp.getVolume() * symbolInfo.getTrade_contract_size()).setScale(0, RoundingMode.HALF_DOWN);
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
                    Захеджировано: %s %s
                    """
                    .formatted(instrument.getLabel(), FormattingTools.formatPrice(hedgedAmount), instrument.getSymbolByMode());
        }).collect(Collectors.joining("\n"));

        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .parseMode("MARKDOWN")
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .text("""
                        *Баланс*
                        
                        Google Sheets: %s
                        
                        %s
                        
                        """.formatted(
                            user.getGoogleSheetId() != null ? "https://docs.google.com/spreadsheets/d/" + user.getGoogleSheetId() : null,
                            positionsStr))
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void noOpenPositions(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text("Нет открытых позиций по доступным инструментам.")
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

}
