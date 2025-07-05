package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@AllArgsConstructor
@Slf4j
public class OrderService {
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;

    @Transactional
    public Order saveCloseOrder(Long telegramId, BigDecimal amount, Mt5Service.OrderDetails orderDetails) {
        AppUser user = userRepo.findById(telegramId).get();

        Order order = new Order();
        order.setUser(user);
        order.setMt5AccNumber(user.getMt5AccNumber());
        order.setInstrument(orderDetails.getRequest().getSymbol());
        order.setDeal(orderDetails.getDeal());
        order.setOrder(orderDetails.getOrder());
        order.setAsk(BigDecimal.valueOf(orderDetails.getAsk()));
        order.setBid(BigDecimal.valueOf(orderDetails.getBid()));
        order.setVolume(BigDecimal.valueOf(orderDetails.getVolume()));
        order.setType(OrderType.CLOSE);
        order.setBaseAmount(amount);
        order.setPrice(BigDecimal.valueOf(orderDetails.getPrice()).setScale(5, RoundingMode.HALF_DOWN));

        orderRepo.save(order);

        return order;
    }

    @Transactional
    public Order saveOpenOrder(Long telegramId, BigDecimal amount, Mt5Service.OrderDetails orderDetails) {
        AppUser user = userRepo.findById(telegramId).get();

        Order order = new Order();
        order.setUser(user);
        order.setMt5AccNumber(user.getMt5AccNumber());
        order.setInstrument(orderDetails.getRequest().getSymbol());
        order.setDeal(orderDetails.getDeal());
        order.setOrder(orderDetails.getOrder());
        order.setAsk(BigDecimal.valueOf(orderDetails.getAsk()));
        order.setBid(BigDecimal.valueOf(orderDetails.getBid()));
        order.setVolume(BigDecimal.valueOf(orderDetails.getVolume()));
        order.setType(OrderType.OPEN);
        order.setBaseAmount(amount);
        order.setPrice(BigDecimal.valueOf(orderDetails.getPrice()));

        orderRepo.save(order);

        return order;
    }
}
