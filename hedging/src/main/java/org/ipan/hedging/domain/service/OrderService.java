package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.LifoPositionRepository;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
    private final LifoPositionRepository lifoPositionRepository;

    @Transactional
    public Order redistributeBaseAmount(PlaceOrderCommand command, VolumeCalculation volumeCalculation) {
        AppUser user = userRepo.findById(command.getUserId()).get();
        AppUserInstrument userInstrument = user.findUserInstrument(command.getInstrumentLabel());
        Instrument instrument = userInstrument.getInstrument();

        Order order = new Order();
        order.setUserInstrument(userInstrument);
        order.setMt5AccNumber(user.getMt5AccNumber());
        order.setType(command.isBuy() ? OrderType.HOUSE_CLIENT_TRANSFER_LONG : OrderType.HOUSE_CLIENT_TRANSFER_SHORT);
        order.setStatus(OrderStatus.COMPLETE);

        order.setAsk(volumeCalculation.getPrice());
        order.setBid(volumeCalculation.getPrice());
        order.setPrice(volumeCalculation.getPrice());

        if (InstrumentMode.LONG.equals(instrument.getMode())) {
            order.setUserEnteredQuoteAmount(BigDecimal.valueOf(command.getUserAmount()));
            order.createLifoPosition(command.getUserAmount());
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
        order.setCorrelationId(UUID.randomUUID());
        order.setUserInstrument(userInstrument);
        order.setMt5AccNumber(user.getMt5AccNumber());
        order.setExecutedVolume(BigDecimal.valueOf(command.getVolume()));
        order.setStatus(OrderStatus.PENDING);
//        order.setBaseAmount(amount);

        order.setType(command.isBuy() ? OrderType.MT5_LONG : OrderType.MT5_SHORT);

        if (InstrumentMode.SHORT.equals(userInstrument.getInstrument().getMode())) {
            order.setExecutedBaseAmount(BigDecimal.valueOf(command.getUserAmount()));
        } else if (InstrumentMode.LONG.equals(userInstrument.getInstrument().getMode())) {
            order.setUserEnteredQuoteAmount(BigDecimal.valueOf(command.getUserAmount()));

            if (OrderType.MT5_LONG.equals(order.getType())) {
                order.createLifoPosition(command.getUserAmount());
            }
        }

        orderRepo.save(order);

        return order;
    }

    @Transactional
    public Order completeOrder(Long id, Mt5ServiceImpl.OrderDetails orderDetails, VolumeCalculation volumeCalculation) {
        Order order = orderRepo.findById(id).get();
        order.setDeal(orderDetails.getDeal());
        order.setOrder(orderDetails.getOrder());
        order.setAsk(BigDecimal.valueOf(orderDetails.getAsk()));
        order.setBid(BigDecimal.valueOf(orderDetails.getBid()));
        order.setPrice(BigDecimal.valueOf(orderDetails.getPrice()).setScale(8, RoundingMode.HALF_DOWN));
        order.setExecutedVolume(BigDecimal.valueOf(orderDetails.getVolume()));
        order.setMt5RespCode(orderDetails.getRetcode());
        order.setMt5RespComment(orderDetails.getRetcode());
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

                List<Long> lifoPositionIds = volumeCalculation.getLifoPositionsForConsumption().stream().map(o -> o.getLifoPositionId()).toList();
                Map<Long, PlannedLifoPositionConsumption> orderIdToConsumption = volumeCalculation.getLifoPositionsForConsumption()
                        .stream()
                        .collect(Collectors.toMap(o -> o.getLifoPositionId(), o -> o));

                List<LifoPosition> allById = lifoPositionRepository.findAllById(lifoPositionIds);
                for (LifoPosition lifoPositionToConsume : allById) {
                    PlannedLifoPositionConsumption plannedLifoPositionConsumption = orderIdToConsumption.get(lifoPositionToConsume.getId());
                    lifoPositionToConsume.decreaseRemainingQuoteAmount(plannedLifoPositionConsumption.getQuoteConsumptionAmount());

                    LifoPositionConsumption orderConsumption = new LifoPositionConsumption();
                    orderConsumption.setParentOrder(order);
                    orderConsumption.setConsumedPosition(lifoPositionToConsume);
                    orderConsumption.setConsumedQuoteAmount(plannedLifoPositionConsumption.getQuoteConsumptionAmount());
                    orderConsumption.setLeftQuoteAmount(lifoPositionToConsume.getRemainingQuoteAmount());
                    if (lifoPositionToConsume.isConsumed()) {
                        log.info("Lifo position {} is consumed, closing it", lifoPositionToConsume.getId());
                        lifoPositionToConsume.setClosedAt(Instant.now());
                    }

                    orderConsumption.setBaseEquivalent(plannedLifoPositionConsumption.getQuoteConsumptionAmount().divide(lifoPositionToConsume.getOrder().getPrice(), RoundingMode.HALF_DOWN));
                    order.getConsumptions().add(orderConsumption);
                }
            }
        } else {
            log.error("Unsupported instrument mode: {}", instrument.getMode());
        }

        return order;
    }

    @Transactional
    public Order rollbackOrder(Long id, Mt5ServiceImpl.OrderDetails orderDetails) {
        log.info("Refunding order with id: {} orderDetails: {}", id, orderDetails);
        Order order = orderRepo.findById(id).get();
        order.setStatus(OrderStatus.ERROR);
        if (orderDetails != null) {
            order.setMt5RespCode(orderDetails.getRetcode());
            order.setMt5RespComment(orderDetails.getRetcode());
        }
        return order;
    }

}
