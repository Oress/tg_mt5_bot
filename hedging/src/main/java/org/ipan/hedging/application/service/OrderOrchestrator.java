package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.application.service.command.PlaceOrderResponse;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.AppUserInstrumentRepository;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class OrderOrchestrator {
    public static final List<Integer> SUCCESS_CODES = List.of(10008, 10009);

    private final UserRepo userRepo;
    private final OrderService orderService;
    private final Mt5Service mt5Service;
    private final VolumeCalculationService volumeCalculationService;
    private final ReportingCalculationService reportingCalculationService;
    private final AppUserInstrumentRepository appUserInstrumentRepository;
    private final OrderMt5InfoFetcherService orderMt5InfoFetcherService;

    public PlaceOrderResponse placeOrder(PlaceOrderCommand command) {
        log.info("Placing order for user: {}, instrument: {}, amount: {}, buy: {}", command.getUserId(), command.getInstrumentLabel(), command.getUserAmount(), command.isBuy());
        AppUser user = userRepo.findByIdWithInstruments(command.getUserId());
        AppUserInstrument userInstrument = user.findUserInstrument(command.getInstrumentLabel());
        Instrument instrument = userInstrument.getInstrument();
        String symbol = user.getSymbolByLabelAndAccType(command.getInstrumentLabel());

        if (symbol == null) {
            // TODO: custom exception
            log.error("Symbol not found for user: {}, instrument: {}", user.getTelegramId(), command.getInstrumentLabel());
            throw new RuntimeException("Symbol not found for user: " + user.getTelegramId() + ", instrument: " + command.getInstrumentLabel());
        }

        if (command.getUserAmount() <= 0) {
            // TODO: custom exception
            log.error("Invalid user amount: {} for user: {}, instrument: {}", command.getUserAmount(), user.getTelegramId(), command.getInstrumentLabel());
            throw new IllegalArgumentException("User amount must be greater than zero");
        }

        boolean successful = false;
        Mt5ServiceImpl.SymbolInfo symbolInfo = mt5Service.symbolInfo(user, symbol);
        Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, symbol);
        log.info("Symbol info: {}, infoTick: {}", symbolInfo, infoTick);

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
        log.info("Estimated volume calculation: {}", estimateVolumeCalculation);

        Order order = null;
        if (estimateVolumeCalculation.getExecutionVolume().compareTo(BigDecimal.ZERO) != 0) {
            // add volume from calculation to the command
            command = command.withVolume(estimateVolumeCalculation.getExecutionVolume().doubleValue());

            // 1. create pending order
            order = orderService.createPendingOrder(command);

            // 2. send mt5 request
            double volumeDouble = estimateVolumeCalculation.getExecutionVolume().doubleValue();
            Mt5ServiceImpl.OrderDetails orderDetails = null;

            try {
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
                reportingCalculationService.recalculateAllForUserInstrument(userInstrument.getId());
                orderMt5InfoFetcherService.fetchInfoForOrderAsync(order.getId());
                successful = true;
            } catch (Exception e) {
                successful = false;
                log.error("Error while placing order for user: {}, symbol: {}, userAmount: {}, volume: {}", command.getUserId(), command.getInstrumentLabel(), command.getUserAmount(), volumeDouble, e);
                if (order != null) {
                    order = orderService.rollbackOrder(order.getId(), orderDetails);
                }
                throw e;
            }
        } else {
            log.info("Estimated execution volume is zero for user: {}, instrument: {}, amount: {}, buy: {}. Redistributing house amount to client.",
                    command.getUserId(), command.getInstrumentLabel(), command.getUserAmount(), command.isBuy());

            // usually this means that the value is small
            order = orderService.redistributeBaseAmount(command, estimateVolumeCalculation);
        }

        return new PlaceOrderResponse(successful, order);
    }

    public PlaceOrderResponse liquidate(Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();
        Instrument instrument = userInstrument.getInstrument();
        log.warn("LIQUIDATE for user: {}, instrument: {}", user.getTelegramId(), instrument.getLabel());

        var builder = PlaceOrderCommand.builder()
                .userId(user.getTelegramId())
                .instrumentLabel(instrument.getLabel())
                .isLiquidation(true);

        PlaceOrderCommand command;
        // e.g. USD/CAD
        if (InstrumentMode.LONG.equals(instrument.getMode())) {
            command = builder
                    .userAmount(userInstrument.getClientOwnedQuoteAmount().intValue())
                    .isBuy(false)
                    .build();
        } else {
            // e.g. EUR/USD
            // TODO: verify
            command = builder
                    .userAmount(userInstrument.getClientOwnedBaseAmount().intValue())
                    .isBuy(true)
                    .build();
        }
        return placeOrder(command);
    }
}


/*
10004 TRADE_RETCODE_REQUOTE

Requote

10006 TRADE_RETCODE_REJECT

Request rejected

10007 TRADE_RETCODE_CANCEL

Request canceled by trader

10008 TRADE_RETCODE_PLACED

Order placed

10009 TRADE_RETCODE_DONE

Request completed

10010 TRADE_RETCODE_DONE_PARTIAL

Only part of the request was completed

10011

TRADE_RETCODE_ERROR

Request processing error

10012

TRADE_RETCODE_TIMEOUT

Request canceled by timeout

10013

TRADE_RETCODE_INVALID

Invalid request

10014

TRADE_RETCODE_INVALID_VOLUME

Invalid volume in the request

10015

TRADE_RETCODE_INVALID_PRICE

Invalid price in the request

10016

TRADE_RETCODE_INVALID_STOPS

Invalid stops in the request

10017

TRADE_RETCODE_TRADE_DISABLED

Trade is disabled

10018

TRADE_RETCODE_MARKET_CLOSED

Market is closed

10019

TRADE_RETCODE_NO_MONEY

There is not enough money to complete the request

10020

TRADE_RETCODE_PRICE_CHANGED

Prices changed

10021

TRADE_RETCODE_PRICE_OFF

There are no quotes to process the request

10022

TRADE_RETCODE_INVALID_EXPIRATION

Invalid order expiration date in the request

10023

TRADE_RETCODE_ORDER_CHANGED

Order state changed

10024

TRADE_RETCODE_TOO_MANY_REQUESTS

Too frequent requests

10025

TRADE_RETCODE_NO_CHANGES

No changes in request

10026

TRADE_RETCODE_SERVER_DISABLES_AT

Autotrading disabled by server

10027

TRADE_RETCODE_CLIENT_DISABLES_AT

Autotrading disabled by client terminal

10028

TRADE_RETCODE_LOCKED

Request locked for processing

10029

TRADE_RETCODE_FROZEN

Order or position frozen

10030

TRADE_RETCODE_INVALID_FILL

Invalid order filling type

10031

TRADE_RETCODE_CONNECTION

No connection with the trade server

10032

TRADE_RETCODE_ONLY_REAL

Operation is allowed only for live accounts

10033

TRADE_RETCODE_LIMIT_ORDERS

The number of pending orders has reached the limit

10034

TRADE_RETCODE_LIMIT_VOLUME

The volume of orders and positions for the symbol has reached the limit

10035

TRADE_RETCODE_INVALID_ORDER

Incorrect or prohibited order type

10036

TRADE_RETCODE_POSITION_CLOSED

Position with the specified POSITION_IDENTIFIER has already been closed

10038

TRADE_RETCODE_INVALID_CLOSE_VOLUME

A close volume exceeds the current position volume

10039

TRADE_RETCODE_CLOSE_ORDER_EXIST

A close order already exists for a specified position. This may happen when working in the hedging system:

when attempting to close a position with an opposite one, while close orders for the position already exist
when attempting to fully or partially close a position if the total volume of the already present close orders and the newly placed one exceeds the current position volume
10040

TRADE_RETCODE_LIMIT_POSITIONS

The number of open positions simultaneously present on an account can be limited by the server settings. After a limit is reached, the server returns the TRADE_RETCODE_LIMIT_POSITIONS error when attempting to place an order. The limitation operates differently depending on the position accounting type:

Netting — number of open positions is considered. When a limit is reached, the platform does not let placing new orders whose execution may increase the number of open positions. In fact, the platform allows placing orders only for the symbols that already have open positions. The current pending orders are not considered since their execution may lead to changes in the current positions but it cannot increase their number.
Hedging — pending orders are considered together with open positions, since a pending order activation always leads to opening a new position. When a limit is reached, the platform does not allow placing both new market orders for opening positions and pending orders.
10041

TRADE_RETCODE_REJECT_CANCEL

The pending order activation request is rejected, the order is canceled

10042

TRADE_RETCODE_LONG_ONLY

The request is rejected, because the "Only long positions are allowed" rule is set for the symbol (POSITION_TYPE_BUY)

10043

TRADE_RETCODE_SHORT_ONLY

The request is rejected, because the "Only short positions are allowed" rule is set for the symbol (POSITION_TYPE_SELL)

10044

TRADE_RETCODE_CLOSE_ONLY

The request is rejected, because the "Only position closing is allowed" rule is set for the symbol

10045

TRADE_RETCODE_FIFO_CLOSE

The request is rejected, because "Position closing is allowed only by FIFO rule" flag is set for the trading account (ACCOUNT_FIFO_CLOSE=true)

10046

TRADE_RETCODE_HEDGE_PROHIBITED

The request is rejected, because the "Opposite positions on a single symbol are disabled" rule is set for the trading account. For example, if the account has a Buy position, then a user cannot open a Sell position or place a pending sell order. The rule is only applied to accounts with hedging accounting system (ACCOUNT_MARGIN_MODE=ACCOUNT_MARGIN_MODE_RETAIL_HEDGING).

* */
