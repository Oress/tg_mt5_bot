package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class OrderService {
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;

    @Transactional
    public Order redistributeBaseAmount(PlaceOrderCommand command, VolumeCalculation volumeCalculation) {
        AppUser user = userRepo.findById(command.getUserId()).get();
        AppUserInstrument userInstrument = user.findUserInstrument(command.getInstrumentLabel());
        Instrument instrument = userInstrument.getInstrument();

        Order order = new Order();
        order.setUserInstrument(userInstrument);
        order.setMt5AccNumber(user.getMt5AccNumber());
        order.setType(OrderType.HOUSE_CLIENT_TRANSFER);
        order.setStatus(OrderStatus.COMPLETE);

        order.setAsk(volumeCalculation.getPrice());
        order.setBid(volumeCalculation.getPrice());
        order.setPrice(volumeCalculation.getPrice());

        if (InstrumentMode.LONG.equals(instrument.getMode())) {
            order.setUserEnteredQuoteAmount(BigDecimal.valueOf(command.getUserAmount()));
            order.setQuoteAmountLeftToUnhedge(BigDecimal.valueOf(command.getUserAmount()));
            order.setHouseBaseAmountFrom(volumeCalculation.getOldHouseOwnedBase());
            order.setHouseBaseAmountTo(volumeCalculation.getNewHouseOwnedBase());
            order.setClientBaseAmountFrom(volumeCalculation.getOldUserOwnedBase());
            order.setClientBaseAmountTo(volumeCalculation.getNewUserOwnedBase());
            order.setUserBaseAmount(volumeCalculation.getExecutionUserOwnedBase());
        } else {
            log.error("Unsupported instrument mode: {}", instrument.getMode());
            throw new RuntimeException();
        }

        orderRepo.save(order);

        if (InstrumentMode.LONG.equals(instrument.getMode())) {
            userInstrument.addUserAndHousePartsLongMode(volumeCalculation);
        } else {
            log.error("Unsupported instrument mode: {}", instrument.getMode());
        }

        return order;
    }

    @Transactional
    public Order createPendingOrder(PlaceOrderCommand command) {
        AppUser user = userRepo.findById(command.getUserId()).get();
        AppUserInstrument userInstrument = user.findUserInstrument(command.getInstrumentLabel());

        Order order = new Order();
        order.setUserInstrument(userInstrument);
        order.setMt5AccNumber(user.getMt5AccNumber());
        order.setExecutedVolume(BigDecimal.valueOf(command.getVolume()));
        order.setStatus(OrderStatus.PENDING);
//        order.setBaseAmount(amount);

        if (InstrumentMode.LONG.equals(userInstrument.getInstrument().getMode())) {
            order.setType(command.isBuy() ? OrderType.MT5_LONG : OrderType.MT5_SHORT);
        } else if (InstrumentMode.SHORT.equals(userInstrument.getInstrument().getMode())) {
            order.setType(command.isBuy() ? OrderType.MT5_SHORT : OrderType.MT5_LONG);
        }

        if (InstrumentMode.SHORT.equals(userInstrument.getInstrument().getMode())) {
            order.setExecutedBaseAmount(BigDecimal.valueOf(command.getUserAmount()));
        } else if (InstrumentMode.LONG.equals(userInstrument.getInstrument().getMode())) {
            order.setUserEnteredQuoteAmount(BigDecimal.valueOf(command.getUserAmount()));

            if (OrderType.MT5_LONG.equals(order.getType())) {
                order.setQuoteAmountLeftToUnhedge(BigDecimal.valueOf(command.getUserAmount()));
            }
        }

        orderRepo.save(order);

        return order;
    }

    @Transactional
    public Order completeOrder(UUID id, Mt5ServiceImpl.OrderDetails orderDetails, VolumeCalculation volumeCalculation) {
        Order order = orderRepo.findById(id).get();
        order.setDeal(orderDetails.getDeal());
        order.setOrder(orderDetails.getOrder());
        order.setAsk(BigDecimal.valueOf(orderDetails.getAsk()));
        order.setBid(BigDecimal.valueOf(orderDetails.getBid()));
        order.setPrice(BigDecimal.valueOf(orderDetails.getPrice()).setScale(8, RoundingMode.HALF_DOWN));
        order.setExecutedVolume(BigDecimal.valueOf(orderDetails.getVolume()));
        order.setStatus(OrderStatus.COMPLETE);

        order.setHouseBaseAmountFrom(volumeCalculation.getOldHouseOwnedBase());
        order.setHouseBaseAmountTo(volumeCalculation.getNewHouseOwnedBase());
        order.setClientBaseAmountFrom(volumeCalculation.getOldUserOwnedBase());
        order.setClientBaseAmountTo(volumeCalculation.getNewUserOwnedBase());
        order.setUserBaseAmount(volumeCalculation.getExecutionUserOwnedBase());
        order.setExecutedBaseAmount(volumeCalculation.getExecutionBase());

        AppUserInstrument userInstrument = order.getUserInstrument();
        Instrument instrument = userInstrument.getInstrument();

        if (InstrumentMode.SHORT.equals(instrument.getMode())) {
            userInstrument.addVolumeShortMode(volumeCalculation);
        } else if (InstrumentMode.LONG.equals(instrument.getMode())) {
            if (OrderType.MT5_LONG.equals(order.getType())) {
                // hedge
                userInstrument.addUserAndHousePartsLongMode(volumeCalculation);
            } else if (OrderType.MT5_SHORT.equals(order.getType())) {
                // unhedge
                userInstrument.subtractUserAndHousePartsLongMode(volumeCalculation);

                List<UUID> orderIds = volumeCalculation.getOrdersForConsumption().stream().map(o -> o.getOrderId()).toList();
                Map<UUID, OrderConsumption> orderIdToConsumption = volumeCalculation.getOrdersForConsumption()
                        .stream()
                        .collect(Collectors.toMap(o -> o.getOrderId(), o -> o));

                List<Order> allById = orderRepo.findAllById(orderIds);
                for (Order orderToConsume : allById) {
                    OrderConsumption orderConsumption = orderIdToConsumption.get(orderToConsume.getId());
                    orderToConsume.decreaseQuoteAmountLeftToUnhedge(orderConsumption.getQuoteConsumptionAmount());
                    // TODO: make a record to the database about consumed orders.
                }
            }
        } else {
            log.error("Unsupported instrument mode: {}", instrument.getMode());
        }

        return order;
    }
}
