package org.ipan.hedging.telegram.handlers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.application.service.*;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.application.service.command.PlaceOrderResponse;
import org.ipan.hedging.domain.exception.CoverageException;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.InstrumentRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
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
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;


//        10004: "Requote",
//        10006: "Запрос отклонен",
//        10007: "Запрос отменен трейдером",
//        10008: "Ордер размещен",
//        10009: "Запрос выполнен",
//        10010: "Была выполнена только часть запроса",
//        10011: "Ошибка обработки запроса",
//        10012: "Запрос отменен из-за тайм-аута",
//        10013: "Неверный запрос",
//        10014: "Неверный объем в запросе",
//        10015: "Неверная цена в запросе",
//        10016: "Неверные остановки в запросе",
//        10017: "Торговля отключена",
//        10018: "Рынок закрыт.",
//        10019: "Недостаточно денег для выполнения запроса",
//        10020: "Цены изменены",
//        10021: "Нет котировок для обработки запроса",
//        10022: "Неверная дата окончания срока действия ордера в запросе",
//        10023: "Состояние ордера изменено",
//        10024: "Слишком частые запросы",
//        10025: "Никаких изменений в запросе.",
//        10026: "Автоторговля отключена сервером",
//        10027: "Автоторговля отключена клиентским терминалом",
//        10028: "Запрос заблокирован для обработки",
//        10029: "Ордер или позиция заморожены",
//        10030: "Неверный тип заполнения ордера",
//        10031: "Нет соединения с торговым сервером",
//        10032: "Операция разрешена только для реальных счетов.",
//        10033: "Количество ожидающих ордеров достигло предела",
//        10034: "Объем ордеров и позиций по символу достиг предела",
//        10035: "Неправильный или запрещенный тип ордера",
//        10036: "Позиция с указанным POSITION_IDENTIFIER уже закрыта",
//        10038: "Объем закрытия превышает объем текущей позиции",
//        10039: "Для указанной позиции уже существует ордер на закрытие.",

@TransitionHandler
@AllArgsConstructor
@Slf4j
@Profile("!test")
public class HedgingHandler {
    public static final double STEP_AMOUNT = 1.0;
    private final UserRepo userRepo;
    private final TelegramState telegramState;
    private final BalanceViews balanceViews;
    private final HedgeViews hedgeViews;
    private final HedgeLabels hedgeLabels;
    private final TerminalBroker terminalBroker;
    private final TelegramMessages telegramMessages;
    private final GoogleSheetsService googleSheetsService;
    private final OrderOrchestrator orderOrchestrator;
    private final InstrumentRepo instrumentRepo;
    private final MainMenuStatsService mainMenuStatsService;

    // Admin act as a client
    @MatchStates({
            @MatchState(state = States.ADMIN_MANAGE_USERS_ACTION_PREVIEW, callbackData = InlineMenuCallbacks.EXECUTE_ORDER_AS_CLIENT),
    })
    public void admin_as_client_start_hedge(UserState adminUserState, Update update) {
        Long userId = telegramState.getOrCreateUserEdit(adminUserState.getTelegramId()).getSelectedUserId();

        log.info("ADMIN_AS_CLIENT. Admin {} requested hedging operation for user {}", adminUserState.getTelegramId(), userId);
        AppUser user = userRepo.findByIdWithInstruments(userId);

        if (!user.hasAccount()) {
            log.warn("ADMIN_AS_CLIENT. user has no account: {}", user.getTelegramId());
            balanceViews.admin_as_client_noAccount(adminUserState, user);
            return;
        }

        if (!user.hasInstruments()) {
            log.warn("ADMIN_AS_CLIENT. user has no available instruments: {}", user.getTelegramId());
            hedgeViews.admin_as_client_noInstruments(adminUserState, user);
            return;
        }

        hedgeViews.promptInstruments(adminUserState, new ArrayList<>(user.getAvailableInstruments()
                .stream().filter(AppUserInstrument::getActive)
                .map(AppUserInstrument::getInstrument)
                .sorted(Comparator.comparing(Instrument::getLabel)).toList()));
        telegramState.updateUserState(adminUserState.getTelegramId(), adminUserState.withState(States.HEDGE_INSTRUMENT_ADMIN_ACT_AS_CLIENT));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_INSTRUMENT_ADMIN_ACT_AS_CLIENT, updateTypes = UpdateType.CALLBACK_QUERY),
    })
    public void admin_as_client_handleInstrument(UserState adminUserState, Update update) {
        log.info("ADMIN_AS_CLIENT. User {} selected instrument for hedging: {}", adminUserState.getTelegramId(), update.getCallbackQuery().getData());
        String instrumentLabel = update.getCallbackQuery().getData();
        if (InlineMenuCallbacks.TO_MAIN_MENU.equals(instrumentLabel)) return;

        Long userId = telegramState.getOrCreateUserEdit(adminUserState.getTelegramId()).getSelectedUserId();
        AppUser targetUser = userRepo.findByIdWithInstruments(userId);
        if (!targetUser.containsInstument(instrumentLabel)) {
            log.error("ADMIN_AS_CLIENT. User has selected instrument that should not be available to them {} {}", adminUserState.getTelegramId(), instrumentLabel);
            return;
        }
        HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(adminUserState.getTelegramId());
        String symbol = targetUser.getSymbolByLabelAndAccType(instrumentLabel);
        Instrument instrument = targetUser.findInstrumentBySymbol(symbol);
        telegramState.updateHedgeOp(adminUserState.getTelegramId(), hedgeOp.withInstrumentLabel(instrumentLabel));
        Mt5Service mt5Service = terminalBroker.getMt5Service(targetUser);
        mt5Service.symbolInfo(targetUser, symbol);

        if (InstrumentMode.SHORT.equals(instrument.getMode())) {
            hedgeViews.showOperationsForShort(adminUserState);
        } else {
            hedgeViews.showOperationsForLong(adminUserState);
        }
        telegramState.updateUserState(adminUserState.getTelegramId(), adminUserState.withState(States.HEDGE_OPERATION_ADMIN_ACT_AS_CLIENT));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_OPERATION_ADMIN_ACT_AS_CLIENT, updateTypes = UpdateType.CALLBACK_QUERY),
    })
    public void admin_as_client_handleOperation(UserState adminUserState, Update update) {
        log.info("ADMIN_AS_CLIENT. User {} selected operation for hedging: {}", adminUserState.getTelegramId(), update.getCallbackQuery().getData());
        // Check if the operation is valid for the selected instrument
        String operation = update.getCallbackQuery().getData();
        if (InlineMenuCallbacks.TO_MAIN_MENU.equals(operation)) return;

        Long userId = telegramState.getOrCreateUserEdit(adminUserState.getTelegramId()).getSelectedUserId();
        AppUser targetUser = userRepo.findByIdWithInstruments(userId);

        if (!InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation) && !InlineMenuCallbacks.HEDGE_OP_BUY.equals(operation)) {
            log.error("ADMIN_AS_CLIENT. Invalid operation selected: {}", operation);
            return;
        }

        HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(adminUserState.getTelegramId());
        String symbol = targetUser.getSymbolByLabelAndAccType(hedgeOp.getInstrumentLabel());
        Mt5Service mt5Service = terminalBroker.getMt5Service(targetUser);
        mt5Service.symbolInfo(targetUser, symbol);
        AppUserInstrument userInstrument = targetUser.findUserInstrument(hedgeOp.getInstrumentLabel());
        Instrument instrumentBySymbol = userInstrument.getInstrument();

        if (isHedgeOut(instrumentBySymbol, operation)) {
            log.info("ADMIN_AS_CLIENT. User {} is trying to hedge out for symbol: {}", adminUserState.getTelegramId(), symbol);
            if (userInstrument.getClientOwnedBaseAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.info("ADMIN_AS_CLIENT. User {} has no open positions for symbol: {}", adminUserState.getTelegramId(), symbol);
                hedgeViews.noActivePositions(adminUserState);
                return;
            } else {
                if (InstrumentMode.LONG.equals(instrumentBySymbol.getMode())) {
                    BigDecimal availToSpend = userInstrument.getClientOwnedQuoteAmount().setScale(0, RoundingMode.HALF_DOWN);
                    log.info("ADMIN_AS_CLIENT. User {} has open positions for symbol: {}, available to hedge out: {}", adminUserState.getTelegramId(), symbol, availToSpend);
                    hedgeViews.promptVolumeForHedgeOutInverse(adminUserState,
                            instrumentBySymbol.getSymbolByMode(),
                            instrumentBySymbol.getLabel(),
                            availToSpend
                    );
                } else {
                    BigDecimal availToSpend = userInstrument.getClientOwnedBaseAmount().setScale(0, RoundingMode.HALF_DOWN);
                    log.info("ADMIN_AS_CLIENT. User {} has open positions for symbol: {}, available to hedge out: {}", adminUserState.getTelegramId(), symbol, availToSpend);

                    hedgeViews.promptVolumeForHedgeOut(adminUserState,
                            instrumentBySymbol.getSymbolByMode(),
                            instrumentBySymbol.getLabel(),
                            availToSpend
                    );
                }
            }
        } else if (isHedge(instrumentBySymbol, operation)) {
            log.info("ADMIN_AS_CLIENT. User {} is trying to hedge for symbol: {}", adminUserState.getTelegramId(), symbol);
            hedgeViews.promptVolumeForHedge(adminUserState,
                    instrumentBySymbol.getSymbolByMode(),
                    instrumentBySymbol.getLabel()
            );
        }
        telegramState.updateHedgeOp(adminUserState.getTelegramId(), hedgeOp.withOperation(operation));
        mt5Service.getPriceForShort(targetUser, symbol);
        telegramState.updateUserState(adminUserState.getTelegramId(), adminUserState.withState(States.HEDGE_VOLUME_ADMIN_ACT_AS_CLIENT));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_VOLUME_ADMIN_ACT_AS_CLIENT, updateTypes = UpdateType.MESSAGE),
    })
    public void admin_as_client_handleVolume(UserState adminUserState, Update update) {
        log.info("ADMIN_AS_CLIENT. User {} entered volume for hedging: {}", adminUserState.getTelegramId(), update.getMessage().getText());

        Long userId = telegramState.getOrCreateUserEdit(adminUserState.getTelegramId()).getSelectedUserId();
        AppUser targetUser = userRepo.findByIdWithInstruments(userId);

        BigDecimal availToSpend = BigDecimal.ZERO;
        String amountStr = update.getMessage().getText();

        try {
            Integer amount = Integer.parseInt(amountStr);
            HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(adminUserState.getTelegramId());
            AppUserInstrument userInstrument = targetUser.findUserInstrument(hedgeOp.getInstrumentLabel());
            Instrument instrument = userInstrument.getInstrument();
            String symbol = targetUser.getSymbolByLabelAndAccType(hedgeOp.getInstrumentLabel());
            Mt5Service mt5Service = terminalBroker.getMt5Service(targetUser);
            var symbolInfo = mt5Service.symbolInfo(targetUser, symbol);

            if (isHedgeOut(instrument, hedgeOp.getOperation())) {
                if (InstrumentMode.LONG.equals(instrument.getMode())) {
                    availToSpend = userInstrument.getClientOwnedQuoteAmount().setScale(0, RoundingMode.HALF_DOWN);
                } else {
                    availToSpend = userInstrument.getClientOwnedBaseAmount().setScale(0, RoundingMode.HALF_DOWN);
                }
                if (amount > availToSpend.intValue()) {
                    log.warn("ADMIN_AS_CLIENT. User {} tried to hedge out with amount greater than available: {} > {}", adminUserState.getTelegramId(), amount, availToSpend);
                    hedgeViews.greaterThanAvailable(adminUserState, availToSpend);
                    return;
                }
            }

//            Double minAmount = symbolInfo.getVolume_min() * symbolInfo.getTrade_contract_size(); // usually 1_000 base units
            Double minAmount = 0.0;
            Double maxAmount = symbolInfo.getVolume_max() * symbolInfo.getTrade_contract_size();  // usually 1_000_000 base units
//            Double stepAmount = symbolInfo.getVolume_step() * symbolInfo.getTrade_contract_size(); // usually 1000 base units
//            Double stepAmount = STEP_AMOUNT;
            if (amount <= minAmount) {
                log.warn("ADMIN_AS_CLIENT. User {} tried to hedge with amount less than minimum: {} < {}", adminUserState.getTelegramId(), amount, minAmount);
                hedgeViews.lessThanMinAmount(adminUserState, minAmount);
                return;
            } else if (amount > maxAmount) {
                log.warn("ADMIN_AS_CLIENT. User {} tried to hedge with amount greater than maximum: {} > {}", adminUserState.getTelegramId(), amount, maxAmount);
                hedgeViews.greaterThanMaxAvailable(adminUserState, BigDecimal.valueOf(maxAmount));
                return;
            }
            /*else if (amount % stepAmount != 0) {
                log.warn("User {} tried to hedge with amount not divisible by step: {} % {} != 0", adminUserState.getTelegramId(), amount, stepAmount);
                hedgeViews.stepWarning(adminUserState, stepAmount);
                return;
            }*/

            Double price = mt5Service.getPriceForShort(targetUser, symbol);
            telegramState.updateHedgeOp(adminUserState.getTelegramId(), hedgeOp.withUserAmount(amount));

            hedgeViews.showPreview(adminUserState,
                    getLabelForOperationAndInstrument(hedgeOp.getOperation(), instrument),
                    instrument.getLabel(),
                    BigDecimal.valueOf(amount),
                    instrument.getSymbolByMode(),
                    price);
            telegramState.updateUserState(adminUserState.getTelegramId(), adminUserState.withState(States.HEDGE_PREVIEW_ADMIN_ACT_AS_CLIENT));
        } catch (NumberFormatException e) {
            log.error("ADMIN_AS_CLIENT. User {} has entered invalid volume: {}", adminUserState.getTelegramId(), amountStr, e);
        }
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_PREVIEW_ADMIN_ACT_AS_CLIENT, callbackData = InlineMenuCallbacks.HEDGE_PLACE_ORDER),
    })
    public void admin_as_client_handlePreviewPlace(UserState adminUserState, Update update) {

        Long userId = telegramState.getOrCreateUserEdit(adminUserState.getTelegramId()).getSelectedUserId();
        AppUser targetUser = userRepo.findByIdWithInstruments(userId);
        HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(adminUserState.getTelegramId());

        String instrumentLabel = hedgeOp.getInstrumentLabel();
        if (!targetUser.containsInstrumentByLabel(instrumentLabel)) {
            log.error("ADMIN_AS_CLIENT. User has selected instrument that should not be available to them {} {}", adminUserState.getTelegramId(), instrumentLabel);
            return;
        }
        String symbol = targetUser.getSymbolByLabelAndAccType(instrumentLabel);
        Instrument instrument = instrumentRepo.findById(instrumentLabel).get();

        Mt5Service mt5Service = terminalBroker.getMt5Service(targetUser);
        var symbolInfo = mt5Service.symbolInfo(targetUser, symbol);
        log.info("ADMIN_AS_CLIENT. Handling hedge preview place for targetUser: {}, {}", adminUserState.getTelegramId(), hedgeOp);

        boolean isBuy = InlineMenuCallbacks.HEDGE_OP_BUY.equals(hedgeOp.getOperation());
        Long ticket = null;

        if (isHedgeOut(instrument, hedgeOp.getOperation())) {
            var tradePos = mt5Service.positionsGet(targetUser, symbol);
            ticket = tradePos == null ? null : tradePos.getTicket();
        }

        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .isBuy(isBuy)
                .userId(userId)
                .userAmount(hedgeOp.getUserAmount())
                .instrumentLabel(instrumentLabel)
                .ticket(ticket)
                .build();

        try {
            PlaceOrderResponse orderPlaceResponse = orderOrchestrator.placeOrder(command);

            Order order = orderPlaceResponse.getOrder();
            // TODO: check status
            if (order != null) {
                AppUserInstrument userInstrument = order.getUserInstrument();
                Instrument instrumentBySymbol = userInstrument.getInstrument();

                BigDecimal currentBalance;
                if (InstrumentMode.LONG.equals(instrument.getMode())) {
                    currentBalance = userInstrument.getClientOwnedQuoteAmount().setScale(0, RoundingMode.HALF_DOWN);
                } else {
                    currentBalance = userInstrument.getClientOwnedBaseAmount().setScale(0, RoundingMode.HALF_DOWN);
//                    Mt5ServiceImpl.TradePos tp = mt5Service.positionsGet(targetUser, symbol);
//                    currentBalance = BigDecimal.valueOf(tp.getVolume()).multiply(BigDecimal.valueOf(symbolInfo.getTrade_contract_size())).setScale(0, RoundingMode.HALF_DOWN);
                }

                if (OrderStatus.COMPLETE.equals(order.getStatus())) {
                    hedgeViews.admin_as_client_successOrder(
                            adminUserState,
                            order.getCorrelationId().toString(),
                            FormattingTools.formatDateToUtc(order.getCreatedAt()),
                            getLabelForOperationAndInstrument(hedgeOp.getOperation(), instrument),
                            instrumentBySymbol.getLabel(),
                            order.getUserEnteredAmount().setScale(0, RoundingMode.HALF_DOWN),
                            order.getPrice().setScale(5, RoundingMode.HALF_DOWN).toString(),
                            currentBalance,
                            instrumentBySymbol.getSymbolByMode(),
                            FormattingTools.formatUser(targetUser)
                    );
                    googleSheetsService.syncUser(userId);
                }
            }
        } catch (CoverageException e) {
            log.error("ADMIN_AS_CLIENT. Coverage exception Error when placing order: ", e);
            hedgeViews.orderIsTooRisky(adminUserState);
        } catch (Exception e) {
            log.error("ADMIN_AS_CLIENT. Error when placing order: ", e);
            hedgeViews.orderFailed(adminUserState);
        }

        // reread targetUser so that we have updated positions and balances
        targetUser = userRepo.findByIdWithInstruments(adminUserState.getTelegramId());
        Message newMenuMsg = telegramMessages.sendUserMainMenuBasedOnRole(adminUserState, adminUserState.getChatId(), targetUser, mainMenuStatsService.createPayload(targetUser));
        telegramState.updateUserState(adminUserState.getTelegramId(), adminUserState
                .withState(States.MAIN_MENU)
                .withChatId(newMenuMsg.getChatId())
                .withMenuMessageId(newMenuMsg.getMessageId()));
    }
    // END Admin act as a client


    @MatchStates({
            @MatchState(state = States.MAIN_MENU, callbackData = InlineMenuCallbacks.MENU_START_HEDGE),
            @MatchState(state = States.HEDGE_PREVIEW, callbackData = InlineMenuCallbacks.HEDGE_CHANGE_ORDER)
    })
    public void start_hedge(UserState userState, Update update) {
        log.info("User {} requested hedging operation", userState.getTelegramId());
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

        hedgeViews.promptInstruments(userState, new ArrayList<>(user.getAvailableInstruments()
                .stream().filter(AppUserInstrument::getActive)
                .map(AppUserInstrument::getInstrument)
                .sorted(Comparator.comparing(Instrument::getLabel)).toList()));
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_INSTRUMENT));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_INSTRUMENT, updateTypes = UpdateType.CALLBACK_QUERY),
    })
    public void handleInstrument(UserState userState, Update update) {
        log.info("User {} selected instrument for hedging: {}", userState.getTelegramId(), update.getCallbackQuery().getData());
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
        telegramState.updateHedgeOp(userState.getTelegramId(), hedgeOp.withInstrumentLabel(instrumentLabel));
        Mt5Service mt5Service = terminalBroker.getMt5Service(user);
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
        log.info("User {} selected operation for hedging: {}", userState.getTelegramId(), update.getCallbackQuery().getData());
        // Check if the operation is valid for the selected instrument
        String operation = update.getCallbackQuery().getData();
        if (InlineMenuCallbacks.TO_MAIN_MENU.equals(operation)) return;

        AppUser user = userRepo.findByIdWithInstruments(userState.getTelegramId());

        if (!InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation) && !InlineMenuCallbacks.HEDGE_OP_BUY.equals(operation)) {
            log.error("Invalid operation selected: {}", operation);
            return;
        }

        HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(userState.getTelegramId());
        String symbol = user.getSymbolByLabelAndAccType(hedgeOp.getInstrumentLabel());
        Mt5Service mt5Service = terminalBroker.getMt5Service(user);
        mt5Service.symbolInfo(user, symbol);
        AppUserInstrument userInstrument = user.findUserInstrument(hedgeOp.getInstrumentLabel());
        Instrument instrumentBySymbol = userInstrument.getInstrument();

        if (isHedgeOut(instrumentBySymbol, operation)) {
            log.info("User {} is trying to hedge out for symbol: {}", userState.getTelegramId(), symbol);
            if (userInstrument.getClientOwnedBaseAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.info("User {} has no open positions for symbol: {}", userState.getTelegramId(), symbol);
                hedgeViews.noActivePositions(userState);
                return;
            } else {
                if (InstrumentMode.LONG.equals(instrumentBySymbol.getMode())) {
                    BigDecimal availToSpend = userInstrument.getClientOwnedQuoteAmount().setScale(0, RoundingMode.HALF_DOWN);
                    log.info("User {} has open positions for symbol: {}, available to hedge out: {}", userState.getTelegramId(), symbol, availToSpend);
                    hedgeViews.promptVolumeForHedgeOutInverse(userState,
                            instrumentBySymbol.getSymbolByMode(),
                            instrumentBySymbol.getLabel(),
                            availToSpend
                    );
                } else {
                    BigDecimal availToSpend = userInstrument.getClientOwnedBaseAmount().setScale(0, RoundingMode.HALF_DOWN);
                    log.info("User {} has open positions for symbol: {}, available to hedge out: {}", userState.getTelegramId(), symbol, availToSpend);

                    hedgeViews.promptVolumeForHedgeOut(userState,
                            instrumentBySymbol.getSymbolByMode(),
                            instrumentBySymbol.getLabel(),
                            availToSpend
                    );
                }
            }
        } else if (isHedge(instrumentBySymbol, operation)) {
            log.info("User {} is trying to hedge for symbol: {}", userState.getTelegramId(), symbol);
            hedgeViews.promptVolumeForHedge(userState,
                    instrumentBySymbol.getSymbolByMode(),
                    instrumentBySymbol.getLabel()
            );
        }
        telegramState.updateHedgeOp(userState.getTelegramId(), hedgeOp.withOperation(operation));
        mt5Service.getPriceForShort(user, symbol);
        telegramState.updateUserState(userState.getTelegramId(), userState.withState(States.HEDGE_VOLUME));
    }

    @MatchStates({
            @MatchState(state = States.HEDGE_VOLUME, updateTypes = UpdateType.MESSAGE),
    })
    public void handleVolume(UserState userState, Update update) {
        log.info("User {} entered volume for hedging: {}", userState.getTelegramId(), update.getMessage().getText());
        AppUser user = userRepo.findByIdWithInstruments(userState.getTelegramId());

        BigDecimal availToSpend = BigDecimal.ZERO;
        String amountStr = update.getMessage().getText();

        try {
            Integer amount = Integer.parseInt(amountStr);
            HedgeOpState hedgeOp = telegramState.getOrCreateHedgeOp(userState.getTelegramId());
            AppUserInstrument userInstrument = user.findUserInstrument(hedgeOp.getInstrumentLabel());
            Instrument instrument = userInstrument.getInstrument();
            String symbol = user.getSymbolByLabelAndAccType(hedgeOp.getInstrumentLabel());
            Mt5Service mt5Service = terminalBroker.getMt5Service(user);
            var symbolInfo = mt5Service.symbolInfo(user, symbol);

            if (isHedgeOut(instrument, hedgeOp.getOperation())) {
                if (InstrumentMode.LONG.equals(instrument.getMode())) {
                    availToSpend = userInstrument.getClientOwnedQuoteAmount().setScale(0, RoundingMode.HALF_DOWN);
                } else {
                    availToSpend = userInstrument.getClientOwnedBaseAmount().setScale(0, RoundingMode.HALF_DOWN);
                }
                if (amount > availToSpend.intValue()) {
                    log.warn("User {} tried to hedge out with amount greater than available: {} > {}", userState.getTelegramId(), amount, availToSpend);
                    hedgeViews.greaterThanAvailable(userState, availToSpend);
                    return;
                }
            }

//            Double minAmount = symbolInfo.getVolume_min() * symbolInfo.getTrade_contract_size(); // usually 1_000 base units
            Double minAmount = 0.0;
            Double maxAmount = symbolInfo.getVolume_max() * symbolInfo.getTrade_contract_size();  // usually 1_000_000 base units
//            Double stepAmount = symbolInfo.getVolume_step() * symbolInfo.getTrade_contract_size(); // usually 1000 base units
//            Double stepAmount = STEP_AMOUNT;
            if (amount <= minAmount) {
                log.warn("User {} tried to hedge with amount less than minimum: {} < {}", userState.getTelegramId(), amount, minAmount);
                hedgeViews.lessThanMinAmount(userState, minAmount);
                return;
            } else if (amount > maxAmount) {
                log.warn("User {} tried to hedge with amount greater than maximum: {} > {}", userState.getTelegramId(), amount, maxAmount);
                hedgeViews.greaterThanMaxAvailable(userState, BigDecimal.valueOf(maxAmount));
                return;
            }
            /*else if (amount % stepAmount != 0) {
                log.warn("User {} tried to hedge with amount not divisible by step: {} % {} != 0", userState.getTelegramId(), amount, stepAmount);
                hedgeViews.stepWarning(userState, stepAmount);
                return;
            }*/

            Double price = mt5Service.getPriceForShort(user, symbol);
            telegramState.updateHedgeOp(userState.getTelegramId(), hedgeOp.withUserAmount(amount));

            hedgeViews.showPreview(userState,
                    getLabelForOperationAndInstrument(hedgeOp.getOperation(), instrument),
                    instrument.getLabel(),
                    BigDecimal.valueOf(amount),
                    instrument.getSymbolByMode(),
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

        String instrumentLabel = hedgeOp.getInstrumentLabel();
        if (!user.containsInstrumentByLabel(instrumentLabel)) {
            log.error("User has selected instrument that should not be available to them {} {}", userState.getTelegramId(), instrumentLabel);
            return;
        }
        String symbol = user.getSymbolByLabelAndAccType(instrumentLabel);
        Instrument instrument = instrumentRepo.findById(instrumentLabel).get();

        Mt5Service mt5Service = terminalBroker.getMt5Service(user);
        var symbolInfo = mt5Service.symbolInfo(user, symbol);
        log.info("Handling hedge preview place for user: {}, {}", userState.getTelegramId(), hedgeOp);

        boolean isBuy = InlineMenuCallbacks.HEDGE_OP_BUY.equals(hedgeOp.getOperation());
        Long ticket = null;

        if (isHedgeOut(instrument, hedgeOp.getOperation())) {
            var tradePos = mt5Service.positionsGet(user, symbol);
            ticket = tradePos == null ? null : tradePos.getTicket();
        }

        PlaceOrderCommand command = PlaceOrderCommand.builder()
                .isBuy(isBuy)
                .userId(userState.getTelegramId())
                .userAmount(hedgeOp.getUserAmount())
                .instrumentLabel(instrumentLabel)
                .ticket(ticket)
                .build();

        try {
            PlaceOrderResponse orderPlaceResponse = orderOrchestrator.placeOrder(command);

            Order order = orderPlaceResponse.getOrder();
            // TODO: check status
            if (order != null) {
                AppUserInstrument userInstrument = order.getUserInstrument();
                Instrument instrumentBySymbol = userInstrument.getInstrument();

                BigDecimal currentBalance;
                if (InstrumentMode.LONG.equals(instrument.getMode())) {
                    currentBalance = userInstrument.getClientOwnedQuoteAmount().setScale(0, RoundingMode.HALF_DOWN);
                } else {
                    currentBalance = userInstrument.getClientOwnedBaseAmount().setScale(0, RoundingMode.HALF_DOWN);
//                    Mt5ServiceImpl.TradePos tp = mt5Service.positionsGet(user, symbol);
//                    currentBalance = BigDecimal.valueOf(tp.getVolume()).multiply(BigDecimal.valueOf(symbolInfo.getTrade_contract_size())).setScale(0, RoundingMode.HALF_DOWN);
                }

                if (OrderStatus.COMPLETE.equals(order.getStatus())) {
                    hedgeViews.successOrder(userState,
                            order.getCorrelationId().toString(),
                            FormattingTools.formatDateToUtc(order.getCreatedAt()),
                            getLabelForOperationAndInstrument(hedgeOp.getOperation(), instrument),
                            instrumentBySymbol.getLabel(),
                            order.getUserEnteredAmount().setScale(0, RoundingMode.HALF_DOWN),
                            order.getPrice().setScale(5, RoundingMode.HALF_DOWN).toString(),
                            currentBalance,
                            instrumentBySymbol.getSymbolByMode()
                    );
                    googleSheetsService.syncUser(userState.getTelegramId());
                }
            }
        } catch (CoverageException e) {
            log.error("Coverage exception Error when placing order: ", e);
            hedgeViews.orderIsTooRisky(userState);
        } catch (Exception e) {
            log.error("Error when placing order: ", e);
            hedgeViews.orderFailed(userState);
        }

        // reread user so that we have updated positions and balances
        user = userRepo.findByIdWithInstruments(userState.getTelegramId());
        Message newMenuMsg = telegramMessages.sendUserMainMenuBasedOnRole(userState, userState.getChatId(), user, mainMenuStatsService.createPayload(user));
        telegramState.updateUserState(userState.getTelegramId(), userState
                .withState(States.MAIN_MENU)
                .withChatId(newMenuMsg.getChatId())
                .withMenuMessageId(newMenuMsg.getMessageId()));
    }


    private boolean isHedge(Instrument instrument, String operation) {
        return (InstrumentMode.SHORT.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation))
                || (InstrumentMode.LONG.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_BUY.equals(operation));
    }

    private boolean isHedgeOut(Instrument instrument, String operation) {
        return (InstrumentMode.SHORT.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_BUY.equals(operation))
                || (InstrumentMode.LONG.equals(instrument.getMode()) && InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation));
    }

    private String getLabelForOperationAndInstrument(String operation, Instrument instrument) {
        return InstrumentMode.LONG.equals(instrument.getMode())
            ? InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation) ? hedgeLabels.buy() : hedgeLabels.sell()
            : InlineMenuCallbacks.HEDGE_OP_SELL.equals(operation) ? hedgeLabels.sell() : hedgeLabels.buy();
    }
}
