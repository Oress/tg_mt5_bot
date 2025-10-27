package org.ipan.hedging.application.service;

import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.Config;
import org.ipan.hedging.application.NoHistoryException;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.OrderType;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;


@Service
@Slf4j
@Profile("!test")
public class OrderMt5InfoFetcherService {
    private final OrderRepo orderRepo;
    private final Mt5Service mt5Service;

    public OrderMt5InfoFetcherService(OrderRepo orderRepo,
                                      @Qualifier(Config.MAIN_MT5) Mt5Service mt5Service) {
        this.orderRepo = orderRepo;
        this.mt5Service = mt5Service;
    }

/*
    @Async
    @Transactional
    @Retryable(maxAttempts = 5)
    public void fetchInfoForOrderAsync(Long orderId) {
        fetchInfoForOrderSync(orderId);
    }
*/

    @Transactional
    @Retryable(maxAttempts = 5)
    public void fetchInfoForOrderSync(Long orderId) {
        Order order = orderRepo.findById(orderId).get();
        if (OrderType.HOUSE_CLIENT_TRANSFER_SHORT.equals(order.getType())
            || OrderType.HOUSE_CLIENT_TRANSFER_LONG.equals(order.getType())) {
            log.info("Fake Order, cannot read Mt5 data for it, order id {}", orderId);
            return;
        }

        AppUser user = order.getUserInstrument().getUser();
        Long ticket = order.getDeal();
        List<Mt5ServiceImpl.TradeDeal> orderHistory = mt5Service.getOrderHistory(user, ticket, order.getDeal());

        if (!orderHistory.isEmpty()) {
            Mt5ServiceImpl.TradeDeal deal = orderHistory.get(0);
            order.setSwap(BigDecimal.valueOf(deal.getSwap()));
            order.setMt5Commission(BigDecimal.valueOf(deal.getCommission()));
            order.setMt5Profit(BigDecimal.valueOf(deal.getProfit()));
            orderRepo.save(order);
        } else {
            log.error("No order history found for order id {}, ticket {}", orderId, ticket);
            throw new NoHistoryException();
        }
    }

}
