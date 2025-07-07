package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.application.service.command.PlaceOrderResponse;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.repository.InstrumentRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.OrderService;
import org.ipan.hedging.domain.service.VolumeCalculation;
import org.ipan.hedging.domain.service.VolumeCalculationParams;
import org.ipan.hedging.domain.service.VolumeCalculationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class OrderOrchestrator {
    public static final List<Integer> SUCCESS_CODES = List.of(10008, 10009, 10010);

    private final UserRepo userRepo;
    private final OrderService orderService;
    private final Mt5Service mt5Service;
    private final VolumeCalculationService volumeCalculationService;

    public PlaceOrderResponse placeOrder(PlaceOrderCommand command) {
        AppUser user = userRepo.findByIdWithInstruments(command.getUserId());
        AppUserInstrument userInstrument = user.findUserInstrument(command.getInstrumentLabel());
        Instrument instrument = userInstrument.getInstrument();
        String symbol = user.getSymbolByLabelAndAccType(command.getInstrumentLabel());

        Mt5ServiceImpl.SymbolInfo symbolInfo = mt5Service.symbolInfo(user, symbol);
        Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, symbol);

        VolumeCalculation estimateVolumeCalculation = volumeCalculationService.calculate(
                VolumeCalculationParams.builder()
                        .isBuy(command.isBuy())
                        .userId(command.getUserId())
                        .instrument(instrument)
                        .userInstrument(userInstrument)
                        .userAmount(command.getUserAmount())
                        .tradeContractSize(symbolInfo.getTrade_contract_size())
                        .tick(infoTick)
                        .build()
        );

        Order order = null;
        VolumeCalculation volumeCalculation = null;
        // TODO: hedge out.
        if (estimateVolumeCalculation.getExecutionVolume().compareTo(BigDecimal.ZERO) != 0) {
            // add volume from calculation to the command
            command = command.withVolume(estimateVolumeCalculation.getExecutionVolume().doubleValue());

            // 1. create pending order
            order = orderService.createPendingOrder(command);
            // TODO: add fields to order to indicate amount that belongs to the house and client.

            // 2. send mt5 request
            double volumeDouble = estimateVolumeCalculation.getExecutionVolume().doubleValue();
            Mt5ServiceImpl.OrderDetails orderDetails = null;
            if (command.isBuy()) {
                orderDetails = mt5Service.placeLongOrder(user, command.getTicket(), symbol, volumeDouble);
            } else {
                orderDetails = mt5Service.placeShortOrder(user, command.getTicket(), symbol, volumeDouble);
            }

            Mt5ServiceImpl.InfoTick actualPrice = new Mt5ServiceImpl.InfoTick();
            actualPrice.setAsk(orderDetails.getPrice());
            actualPrice.setBid(orderDetails.getPrice());

            // TODO: compare actual and estimated volumes.
            // Assuming that rate cannot change drastically during the order placement, it should be fine.
            // We should get the same volume as estimated, with possibly slightly different base amounts(in case of Long mode).

            VolumeCalculation actualVolumeCalculation = volumeCalculationService.calculate(
                VolumeCalculationParams.builder()
                        .isBuy(command.isBuy())
                        .userId(command.getUserId())
                        .instrument(instrument)
                        .userInstrument(userInstrument)
                        .userAmount(command.getUserAmount())
                        .tradeContractSize(symbolInfo.getTrade_contract_size())
                        .tick(actualPrice)
                        .build()
                );

            if (orderDetails == null) {
                log.error("Failed to CLOSE short order for user: {}, symbol: {}, userAmount: {}", command.getUserId(), command.getInstrumentLabel(), command.getUserAmount());
                throw new RuntimeException("Something went wrong while placing order");
            } else if (!SUCCESS_CODES.contains(orderDetails.getRetcode())) {
                log.error("RETURN CODE IS NOT SUCCESSFUL CLOSE order for user: {}, symbol: {}, userAmount: {}, {}", command.getUserId(), command.getInstrumentLabel(), command.getUserAmount(), orderDetails);
                // TODO: for now runtime ex. but later replace it with custom exceptions
                throw new RuntimeException("Something went wrong while placing order");
            }
            // 3. update order status to COMPLETE and calculate balances of user and the house
            order = orderService.completeOrder(order.getId(), orderDetails, actualVolumeCalculation);
        } else {
            // usually this means that the value is small
            // TODO: handle orders that have zero volume. Just redistribute house amount to client.
            order = orderService.redistributeBaseAmount(command, estimateVolumeCalculation);

        }

        return new PlaceOrderResponse(order);
    }
}
