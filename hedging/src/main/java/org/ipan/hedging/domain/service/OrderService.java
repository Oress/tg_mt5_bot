package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.InstrumentRepo;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.commands.users.CreateUserCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class OrderService {
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;

    @Transactional
    public Order saveCloseOrder(Long telegramId, Integer amount, Mt5Service.OrderDetails orderDetails) {
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
        order.setType(OrderType.SHORT_CLOSE);
        order.setEuroAmount(BigDecimal.valueOf(amount));
        order.setPrice(BigDecimal.valueOf(orderDetails.getPrice()));

        orderRepo.save(order);

        return order;
    }

    @Transactional
    public Order saveOpenOrder(Long telegramId, Integer amount, Mt5Service.OrderDetails orderDetails) {
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
        order.setType(OrderType.SHORT);
        order.setEuroAmount(BigDecimal.valueOf(amount));
        order.setPrice(BigDecimal.valueOf(orderDetails.getPrice()));

        orderRepo.save(order);

        return order;
    }
}
