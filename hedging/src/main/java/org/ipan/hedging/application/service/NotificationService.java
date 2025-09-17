package org.ipan.hedging.application.service;

import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.telegram.TelegramMessages;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;

@Service
@Slf4j
public class NotificationService {
    private final Long tgGroupId;
    private final TelegramMessages telegramMessages;

    public NotificationService(
            @Value("${app.notification.tggroupid}") Long tgGroupId,
            TelegramMessages telegramMessages) {
        this.tgGroupId = tgGroupId;
        this.telegramMessages = telegramMessages;
    }

    public void sendNotificationAboutOrder(Order order) {
        if (order == null) return;

        String status = switch (order.getStatus()) {
            case ERROR -> "❌ Ошибка";
            case COMPLETE -> "✅ Выполнен";
            default -> "Неизвестен";
        };
        String type = switch (order.getType()) {
            case MT5_SHORT -> "Шорт";
            case MT5_LONG -> "Лонг";
            case HOUSE_CLIENT_TRANSFER_SHORT -> "Шорт (внутренний перевод)";
            case HOUSE_CLIENT_TRANSFER_LONG -> "Лонг (внутренний перевод)";
            default -> "Неизвестен";
        };

        String msg = String.format("""
                        📢 Новый ордер!

                       Тип: %s
                       Статус: %s
                       Инструмент: %s
                       Сумма: %s
                       Фикс: %s
                       Время создания: %s

                       MT5 Комиссия: %s
                       MT5 Прибыль: %s
                       MT5 Свап: %s
                       MT5 Код: %s

                       ID: %d
                       """
                ,
                type,
                status,
                order.getUserInstrument().getInstrument().getLabel(),
                order.getUserEnteredAmount().setScale(0, RoundingMode.HALF_DOWN),
                order.getPrice().setScale(5, RoundingMode.HALF_DOWN),
                FormattingTools.formatDateToUtc(order.getCreatedAt()),

                order.getMt5Commission().setScale(2, RoundingMode.HALF_DOWN),
                order.getMt5Profit().setScale(2, RoundingMode.HALF_DOWN),
                order.getSwap().setScale(2, RoundingMode.HALF_DOWN),
                order.getMt5RespCode(),

                order.getId()
        );
        telegramMessages.sendOrderPlacedNotification(tgGroupId, msg);
    }
}