package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.domain.model.InstrumentMode;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.OrderService;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.TelegramMessages;
import org.ipan.hedging.telegram.i18n.HedgeLabels;
import org.ipan.hedging.telegram.state.HedgeOpState;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.MatchState;
import org.ipan.hedging.telegram.statetransitions.MatchStates;
import org.ipan.hedging.telegram.statetransitions.TransitionHandler;
import org.ipan.hedging.telegram.statetransitions.UpdateType;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.ipan.hedging.telegram.views.BalanceViews;
import org.ipan.hedging.telegram.views.HedgeViews;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@TransitionHandler
@AllArgsConstructor
@Slf4j
public class HedgingHandler {
    public static final List<Integer> SUCCESS_CODES = List.of(10008, 10009, 10010);
    private final OrderService orderService;
    private final UserRepo userRepo;
    private final TelegramState telegramState;
    private final BalanceViews balanceViews;
    private final HedgeViews hedgeViews;
    private final HedgeLabels hedgeLabels;
    private final Mt5Service mt5Service;
    private final TelegramMessages telegramMessages;

    @MatchStates({
            @MatchState(state = States.MAIN_MENU, callbackData = InlineMenuCallbacks.MENU_START_HEDGE),
            @MatchState(state = States.HEDGE_PREVIEW, callbackData = InlineMenuCallbacks.HEDGE_CHANGE_ORDER)
    })
    public void start_hedge(UserState userState, Update update) {
        AppUser user = userRepo.findByIdWithInstruments(userState.getTelegramId());

        if (!user.hasAccount()) {
            log.warn("user has no account: {}", user.getTelegramId());
            balanceViews.noAccount(userState, user);
            return;
        }

        if (!user.hasInstruments()) {
            log.warn("user has no available instruments: {}", user.getTelegramId());
            hedgeViews.noInstruments(userState, user);
            return;
        }

        hedgeViews.promptInstruments(userState, new ArrayList<>(user.getAvailableInstruments().stream().sorted(Comparator.comparing(i -> i.getLabel())).toList()));
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_INSTRUMENT));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_INSTRUMENT, updateTypes = UpdateType.CALLBACK_QUERY),
    })
    public void handleInstrument(UserState userState, Update update) {
        String instrumentLabel = update.getCallbackQuery().getData();
        if (InlineMenuCallbacks.TO_MAIN_MENU.equals(instrumentLabel)) return;

        AppUser user = userRepo.findByIdWithInstruments(userState.getTelegramId());
        if (!user.containsInstument(instrumentLabel)) {
            log.error("User has selected instrument that should not be available to them {} {}", userState.getTelegramId(), instrumentLabel);
            return;
        }
        HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(userState.getTelegramId());
        String symbol = user.getSymbolByLabelAndAccType(instrumentLabel);
        Instrument instrument = user.findInstrumentBySymbol(symbol);
        telegramState.updateHedgeOp(userState.getTelegramId(), hedgeOp.withSymbol(symbol));
        mt5Service.symbolInfo(user, symbol);

        if (InstrumentMode.SHORT.equals(instrument.getMode())) {
            hedgeViews.showOperationsForShort(userState);
        } else {
            hedgeViews.showOperationsForLong(userState);
        }
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_OPERATION));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_OPERATION, updateTypes = UpdateType.CALLBACK_QUERY),
    })
    public void handleOperation(UserState userState, Update update) {
        // Check if the operation is valid for the selected instrument
        String operation = update.getCallbackQuery().getData();
        if (InlineMenuCallbacks.TO_MAIN_MENU.equals(operation)) return;

        AppUser user = userRepo.findByIdWithInstruments(userState.getTelegramId());

        if (!InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation) && !InlineMenuCallbacks.HEDGE_OP_BUY.equals(operation)) {
            log.error("Invalid operation selected: {}", operation);
            return;
        }

        HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(userState.getTelegramId());
        var symbolInfo = mt5Service.symbolInfo(user, hedgeOp.getSymbol());
        Instrument instrumentBySymbol = user.findInstrumentBySymbol(hedgeOp.getSymbol());

        if (canHedgeOut(instrumentBySymbol, operation)) {
            Mt5Service.TradePos tradePos = mt5Service.positionsGet(user, hedgeOp.getSymbol());
            if (tradePos == null) {
                hedgeViews.noActivePositions(userState);
                return;
            } else {
                BigDecimal availToSpend = BigDecimal.valueOf(tradePos.getVolume() * symbolInfo.getTrade_contract_size()).setScale(0, RoundingMode.HALF_DOWN);
                hedgeViews.promptVolumeForHedgeOut(userState,
                        instrumentBySymbol.getSymbolByMode(),
                        instrumentBySymbol.getLabel(),
                        availToSpend.toString()
                );
            }
        } else if (canHedge(instrumentBySymbol, operation)) {
            hedgeViews.promptVolumeForHedge(userState,
                    instrumentBySymbol.getSymbolByMode(),
                    instrumentBySymbol.getLabel()
                    );
        }
        telegramState.updateHedgeOp(userState.getTelegramId(), hedgeOp.withOperation(operation));
        mt5Service.getPriceForShort(user, hedgeOp.getSymbol());
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_VOLUME));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_VOLUME, updateTypes = UpdateType.MESSAGE),
    })
    public void handleVolume(UserState userState, Update update) {
        AppUser user = userRepo.findByIdWithInstruments(userState.getTelegramId());

        BigDecimal availToSpend = BigDecimal.ZERO;
        String amountStr = update.getMessage().getText();

        try {
            Integer amount = Integer.parseInt(amountStr);
            HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(userState.getTelegramId());
            var symbolInfo = mt5Service.symbolInfo(user, hedgeOp.getSymbol());

            if (InlineMenuCallbacks.HEDGE_OP_BUY.equals(hedgeOp.getOperation())) {
                var tradePos = mt5Service.positionsGet(user, hedgeOp.getSymbol());
                // rouding to 0 should not be a problem at all since contract size is integer > 1000 and volume is 0.01 with same step
                availToSpend = BigDecimal.valueOf(tradePos.getVolume()).multiply(BigDecimal.valueOf(symbolInfo.getTrade_contract_size())).setScale(0, RoundingMode.HALF_DOWN);

                if (amount > availToSpend.intValue()) {
                    hedgeViews.greaterThanAvailable(userState, availToSpend);
                    return;
                }
            }

            Double minAmount = symbolInfo.getVolume_min() * symbolInfo.getTrade_contract_size();
            Double maxAmount = symbolInfo.getVolume_max() * symbolInfo.getTrade_contract_size();
            Double stepAmount = symbolInfo.getVolume_step() * symbolInfo.getTrade_contract_size();
            if (amount < minAmount) {
                hedgeViews.lessThanMinAmount(userState, minAmount);
                return;
            } else if (amount > maxAmount) {
                hedgeViews.greaterThanAvailable(userState, BigDecimal.valueOf(maxAmount));
                return;
            } else if (amount % stepAmount != 0) {
                hedgeViews.stepWarning(userState, stepAmount);
                return;
            }

            Double price = mt5Service.getPriceForShort(user, hedgeOp.getSymbol());
            telegramState.updateHedgeOp(userState.getTelegramId(), hedgeOp.withVolume(new BigDecimal(1.0 * amount / symbolInfo.getTrade_contract_size())));

            Instrument instrumentBySymbol = user.findInstrumentBySymbol(hedgeOp.getSymbol());
            hedgeViews.showPreview(userState,
                    InlineMenuCallbacks.HEDGE_OP_SELL.equals(hedgeOp.getOperation()) ? hedgeLabels.sell() : hedgeLabels.buy(),
                    instrumentBySymbol.getLabel(),
                    amountStr,
                    instrumentBySymbol.getSymbolByMode(),
                    price);
            telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_PREVIEW));
        } catch (NumberFormatException e) {
            log.error("User {} has entered invalid volume: {}", userState.getTelegramId(), amountStr, e);
        }
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_PREVIEW, callbackData = InlineMenuCallbacks.HEDGE_PLACE_ORDER),
    })
    public void handlePreviewPlace(UserState userState, Update update) {
        AppUser user = userRepo.findByIdWithInstruments(userState.getTelegramId());
        HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(userState.getTelegramId());

        var symbolInfo = mt5Service.symbolInfo(user, hedgeOp.getSymbol());
        BigDecimal amount = hedgeOp.getVolume().multiply(BigDecimal.valueOf(symbolInfo.getTrade_contract_size())).setScale(0, RoundingMode.HALF_DOWN);

        log.info("Handling hedge preview place for user: {}, {}", userState.getTelegramId(), hedgeOp);

        Order order = null;
        if (InlineMenuCallbacks.HEDGE_OP_BUY.equals(hedgeOp.getOperation())) {
            var tradePos = mt5Service.positionsGet(user, hedgeOp.getSymbol());
            if (tradePos != null) {
                var orderDetails = mt5Service.closeShortOrder(user, tradePos.getTicket(), hedgeOp.getSymbol(), hedgeOp.getVolume().doubleValue());

                if (orderDetails == null) {
                    log.error("Failed to CLOSE short order for user: {}, symbol: {}, volume: {}", userState.getTelegramId(), hedgeOp.getSymbol(), hedgeOp.getVolume());
                    return;
                } else if (!SUCCESS_CODES.contains(orderDetails.getRetcode())) {
                    log.error("RETURN CODE IS NOT SUCCESSFUL CLOSE order for user: {}, symbol: {}, volume: {}, {}", userState.getTelegramId(), hedgeOp.getSymbol(), hedgeOp.getVolume(), orderDetails);
                    hedgeViews.orderFailed(userState, orderDetails.getRetcode(), orderDetails.getComment());
                    return;
                }

                order = orderService.saveCloseOrder(userState.getTelegramId(), amount, orderDetails);
            }
        } else if (InlineMenuCallbacks.HEDGE_OP_SELL.equals(hedgeOp.getOperation())) {
            var orderDetails = mt5Service.placeShortOrder(user, hedgeOp.getSymbol(), hedgeOp.getVolume().doubleValue());

            if (orderDetails == null) {
                log.error("Failed to OPEN short order for user: {}, symbol: {}, volume: {}", userState.getTelegramId(), hedgeOp.getSymbol(), hedgeOp.getVolume());
                return;
            } else if (!SUCCESS_CODES.contains(orderDetails.getRetcode())) {
                log.error("RETURN CODE IS NOT SUCCESSFUL OPEN order for user: {}, symbol: {}, volume: {}, {}", userState.getTelegramId(), hedgeOp.getSymbol(), hedgeOp.getVolume(), orderDetails);
                hedgeViews.orderFailed(userState, orderDetails.getRetcode(), orderDetails.getComment());
                return;
            }

            order = orderService.saveOpenOrder(userState.getTelegramId(), amount, orderDetails);
        } else {
            log.error("Invalid operation in hedge preview: {}", hedgeOp.getOperation());
            return;
        }

        if (order != null) {
            Mt5Service.TradePos tp = mt5Service.positionsGet(user, hedgeOp.getSymbol());
            BigDecimal baseEquivalent = tp == null ? BigDecimal.ZERO : BigDecimal.valueOf(tp.getVolume() * symbolInfo.getTrade_contract_size()).setScale(0, RoundingMode.HALF_DOWN);
            hedgeViews.successOrder(userState,
                    order.getId().toString(),
                    FormattingTools.formatDateToUtc(order.getCreatedAt()),
                    InlineMenuCallbacks.HEDGE_OP_SELL.equals(hedgeOp.getOperation()) ? hedgeLabels.sell() : hedgeLabels.buy(),
                    order.getInstrument(),
                    order.getEuroAmount().toString(),
                    order.getPrice().toString(),
                    baseEquivalent.toString(),
                    user.findInstrumentBySymbol(order.getInstrument()).getSymbolByMode()
            );
        }

        Message newMenuMsg = telegramMessages.sendUserMainMenuBasedOnRole(userState, userState.getChatId(), user);
        telegramState.updateUserState(userState.getTelegramId(), userState
                .withState(States.MAIN_MENU)
                .withChatId(newMenuMsg.getChatId())
                .withMenuMessageId(newMenuMsg.getMessageId()));
    }


    private boolean canHedge(Instrument instrument, String operation) {
        return (InstrumentMode.SHORT.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation))
                || (InstrumentMode.LONG.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_BUY.equals(operation));
    }

    private boolean canHedgeOut(Instrument instrument, String operation) {
        return (InstrumentMode.SHORT.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_BUY.equals(operation))
                || (InstrumentMode.LONG.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation));
    }
}
