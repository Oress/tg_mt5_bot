package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.HouseOwnedPositionRepository;
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
    private final HouseOwnedPositionRepository houseOwnedPositionRepository;

    @Transactional
    public Order redistributeBaseAmount(PlaceOrderCommand command, VolumeCalculation volumeCalculation) {
        AppUser user = userRepo.findById(command.getUserId()).get();
        AppUserInstrument userInstrument = user.findUserInstrument(command.getInstrumentLabel());
        Instrument instrument = userInstrument.getInstrument();

        Order order = new Order();
        order.setCorrelationId(UUID.randomUUID());
        order.setUserInstrument(userInstrument);
        order.setMt5AccNumber(user.getMt5AccNumber());
        order.setCommissionCoef(userInstrument.getCommissionCoef());
        order.setType(command.isBuy() ? OrderType.HOUSE_CLIENT_TRANSFER_LONG : OrderType.HOUSE_CLIENT_TRANSFER_SHORT);
        order.setStatus(OrderStatus.COMPLETE);

        order.setAsk(volumeCalculation.getPrice());
        order.setBid(volumeCalculation.getPrice());
        order.setPrice(volumeCalculation.getPrice());

        order.setUserEnteredQuoteAmount(BigDecimal.valueOf(command.getUserAmount()));
        order.setHouseBaseAmountFrom(volumeCalculation.getOldHouseOwnedBase());
        order.setHouseBaseAmountTo(volumeCalculation.getNewHouseOwnedBase());
        order.setClientBaseAmountFrom(volumeCalculation.getOldUserOwnedBase());
        order.setClientBaseAmountTo(volumeCalculation.getNewUserOwnedBase());
        order.setUserBaseAmount(volumeCalculation.getExecutionUserOwnedBase());

        // TODO: this is for now. Later I will implement hedge logic for amounts < min lot size.
        if (InstrumentMode.LONG.equals(instrument.getMode())) {
            if (command.isBuy()) {
                // hedge
                userInstrument.addUserAndHousePartsLongMode(volumeCalculation);
            } else {
                // unhedge
                userInstrument.subtractUserAndHousePartsLongMode(volumeCalculation);
            }

        } else {
            // effectively add and subtract work the same way for short mode
            userInstrument.addUserAndHousePartsLongMode(volumeCalculation);
        }

        // short mode orders do not need lifo positions
        if (!order.isHedgeOut() && InstrumentMode.LONG.equals(instrument.getMode())) {
            order.createLifoPosition(order.getUserEnteredAmount().intValue());
        }

        calculateLifoConsumptions(volumeCalculation, order);
        calculateHousePositions(order, volumeCalculation);

        orderRepo.save(order);

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
        order.setCommissionCoef(userInstrument.getCommissionCoef());
        order.setExecutedVolume(BigDecimal.valueOf(command.getVolume()));
        order.setStatus(OrderStatus.PENDING);

        order.setType(command.isBuy() ? OrderType.MT5_LONG : OrderType.MT5_SHORT);

        if (command.isLiquidation()) {
            order.setSystemType(OrderSystemType.LIQUIDATION);
        }

        if (InstrumentMode.SHORT.equals(userInstrument.getInstrument().getMode())) {
            order.setExecutedBaseAmount(BigDecimal.valueOf(command.getUserAmount()));
        } else if (InstrumentMode.LONG.equals(userInstrument.getInstrument().getMode())) {
            order.setUserEnteredQuoteAmount(BigDecimal.valueOf(command.getUserAmount()));
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
            if (OrderType.MT5_LONG.equals(order.getType())) {
                // unhedge
                userInstrument.subtractUserAndHousePartsShortMode(volumeCalculation);
            } else if (OrderType.MT5_SHORT.equals(order.getType())) {
                // hedge
                userInstrument.addVolumeShortMode(volumeCalculation);
            }
        } else if (InstrumentMode.LONG.equals(instrument.getMode())) {
            if (OrderType.MT5_LONG.equals(order.getType())) {
                // hedge
                userInstrument.addUserAndHousePartsLongMode(volumeCalculation);
            } else if (OrderType.MT5_SHORT.equals(order.getType())) {
                // unhedge
                userInstrument.subtractUserAndHousePartsLongMode(volumeCalculation);
            }
        } else {
            log.error("Unsupported instrument mode: {}", instrument.getMode());
        }

        // short mode orders do not need lifo positions
        if(!order.isHedgeOut() && InstrumentMode.LONG.equals(instrument.getMode())) {
            order.createLifoPosition(order.getUserEnteredAmount().intValue());
        }

        calculateLifoConsumptions(volumeCalculation, order);
        calculateHousePositions(order, volumeCalculation);

        return order;
    }

    private void calculateLifoConsumptions(VolumeCalculation volumeCalculation, Order order) {
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
            orderConsumption.setConsumedAmount(plannedLifoPositionConsumption.getQuoteConsumptionAmount());
            orderConsumption.setLeftAmount(lifoPositionToConsume.getRemainingAmount());
            if (lifoPositionToConsume.isConsumed()) {
                log.info("Lifo position {} is consumed, closing it", lifoPositionToConsume.getId());
                lifoPositionToConsume.setClosedAt(Instant.now());
            }

//            orderConsumption.setBaseEquivalent(plannedLifoPositionConsumption.getQuoteConsumptionAmount().divide(lifoPositionToConsume.getOrder().getPrice(), RoundingMode.HALF_DOWN));
            order.getConsumptions().add(orderConsumption);
        }
    }

    private void calculateHousePositions(Order order, VolumeCalculation volumeCalculation) {
        CreateHousePosition createHousePosition = volumeCalculation.getCreateHousePosition();
        if (createHousePosition != null) {
            order.createHouseOwnedPosition(createHousePosition.getAmount());
        }

        List<PlannedHousePositionConsumption> housePositionsForConsumption = volumeCalculation.getHousePositionsForConsumption();
        List<Long> housePositionIds = housePositionsForConsumption.stream().map(o -> o.getHousePositionId()).toList();
        Map<Long, PlannedHousePositionConsumption> orderIdToConsumption = housePositionsForConsumption
                .stream()
                .collect(Collectors.toMap(o -> o.getHousePositionId(), o -> o));

        List<HouseOwnedPosition> allById = houseOwnedPositionRepository.findAllById(housePositionIds);
        for (HouseOwnedPosition houseOwnedPosition : allById) {
            PlannedHousePositionConsumption plannedHousePositionConsumption = orderIdToConsumption.get(houseOwnedPosition.getId());
            houseOwnedPosition.decreaseRemainingAmount(plannedHousePositionConsumption.getConsumptionAmount());

            BigDecimal pnl = null;
            if (HouseOwnedPositionType.LONG.equals(houseOwnedPosition.getType())) {
                // PnL = position_usd * ((exit_rate / entry_rate) - 1)
                pnl = plannedHousePositionConsumption.getConsumptionAmount().multiply(
                        volumeCalculation.getPrice().divide(houseOwnedPosition.getOrder().getPrice(), 8, RoundingMode.HALF_DOWN)
                                .subtract(BigDecimal.ONE)
                );
            } else if (HouseOwnedPositionType.SHORT.equals(houseOwnedPosition.getType())) {
                // PnL = -1 * position_usd * (( exit_rate/entry_rate) - 1)
                pnl = plannedHousePositionConsumption.getConsumptionAmount().multiply(
                        volumeCalculation.getPrice().divide(houseOwnedPosition.getOrder().getPrice(), 8, RoundingMode.HALF_DOWN)
                                .subtract(BigDecimal.ONE)
                ).negate();
            } else {
                log.error("Unsupported house position type: {}", houseOwnedPosition.getType());
            }

            HousePositionConsumption housePositionConsumption = new HousePositionConsumption();
            housePositionConsumption.setParentOrder(order);
            housePositionConsumption.setConsumedPosition(houseOwnedPosition);
            housePositionConsumption.setConsumedAmount(plannedHousePositionConsumption.getConsumptionAmount());
            housePositionConsumption.setPnl(pnl);
            housePositionConsumption.setLeftAmount(houseOwnedPosition.getRemainingAmount());
            if (houseOwnedPosition.isConsumed()) {
                log.info("HOUSE position {} is consumed, closing it", houseOwnedPosition.getId());
                houseOwnedPosition.setClosedAt(Instant.now());
            }

            order.getHousePositionConsumptions().add(housePositionConsumption);
        }
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
        // TODO: cancel lifo and house positions
        return order;
    }

}
