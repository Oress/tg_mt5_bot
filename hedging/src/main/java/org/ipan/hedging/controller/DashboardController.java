package org.ipan.hedging.controller;

import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.Config;
import org.ipan.hedging.application.service.*;
import org.ipan.hedging.controller.dto.*;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.exception.InvalidAccountCredentialsException;
import org.ipan.hedging.domain.exception.UserWithSameAccountNumberAlreadyExists;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.*;
import org.ipan.hedging.domain.service.*;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@Profile("!test")
@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
@Slf4j
public class DashboardController {
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;
    private final UserTelegramActivityRepository userTelegramActivityRepo;
    private final UserBalanceActionsRepository userBalanceActionsRepository;
    private final AppUserInstrumentRepository appUserInstrumentRepository;
    private final UserService userService;
    private final Mt5Service mt5Service;
    private final ReportingCalculationRepository reportingCalculationRepository;
    private final UserBalanceService userBalanceService;
    private final UnrealizedPnlCalculator unrealizedPnlCalculator;
    private final HousePositionConsumptionRepository housePositionConsumptionRepository;
    private final OrderOrchestrator orderOrchestrator;
    private final GoogleSheetsService googleSheetsService;
    private final ReportingCalculationService reportingCalculationService;
    private final CommissionWithdrawalRepository commissionWithdrawalRepository;
    private final CommissionWithdrawalService commissionWithdrawalService;

    public DashboardController(UserRepo userRepo,
                               OrderRepo orderRepo,
                               UserTelegramActivityRepository userTelegramActivityRepo,
                               UserBalanceActionsRepository userBalanceActionsRepository,
                               AppUserInstrumentRepository appUserInstrumentRepository,
                               UserService userService,
                               @Qualifier(Config.AUX_MT5)
                               Mt5Service mt5Service,
                               ReportingCalculationRepository reportingCalculationRepository,
                               UserBalanceService userBalanceService,
                               UnrealizedPnlCalculator unrealizedPnlCalculator, HousePositionConsumptionRepository housePositionConsumptionRepository, OrderService orderService, OrderOrchestrator orderOrchestrator, GoogleSheetsService googleSheetsService, ReportingCalculationService reportingCalculationService, CommissionWithdrawalRepository commissionWithdrawalRepository, CommissionWithdrawalService commissionWithdrawalService) {
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.userTelegramActivityRepo = userTelegramActivityRepo;
        this.userBalanceActionsRepository = userBalanceActionsRepository;
        this.appUserInstrumentRepository = appUserInstrumentRepository;
        this.userService = userService;
        this.mt5Service = mt5Service;
        this.reportingCalculationRepository = reportingCalculationRepository;
        this.userBalanceService = userBalanceService;
        this.unrealizedPnlCalculator = unrealizedPnlCalculator;
        this.housePositionConsumptionRepository = housePositionConsumptionRepository;
        this.orderOrchestrator = orderOrchestrator;
        this.googleSheetsService = googleSheetsService;
        this.reportingCalculationService = reportingCalculationService;
        this.commissionWithdrawalRepository = commissionWithdrawalRepository;
        this.commissionWithdrawalService = commissionWithdrawalService;
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<UserDto> searchUsers(@RequestParam(required = false) String query) {
        query = query != null ? "%" + query.trim() + "%"  : "%";
        return userRepo.searchForUsers(query).stream().map(this::mapToDto).toList();
    }

    @GetMapping("/users/{telegramId}")
    @Transactional(readOnly = true)
    public ResponseEntity<UserDto> openUser(@PathVariable Long telegramId) {
        return userRepo
                .findById(telegramId)
                .map(u -> ResponseEntity.ok(mapToDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/users/{telegramId}/instruments")
    @Transactional(readOnly = true)
    public ApiResponse<List<UserInstrumentDto>> getUserInstruments(@PathVariable Long telegramId) {
        List<UserInstrumentDto> result = new ArrayList<>();
        AppUser byId = userRepo.findById(telegramId).get();
        for (AppUserInstrument availableInstrument : byId.getAvailableInstruments().stream().filter(i -> i.getActive()).toList()) {
            BigDecimal mt5Diff = orderRepo.getMt5DiffPerInstrument(availableInstrument.getId());
            BigDecimal swapPerInstrument = orderRepo.getSwapPerInstrument(availableInstrument.getId());
            BigDecimal balanceForTheUserInstrument = reportingCalculationRepository.getBalanceForTheUserInstrument(availableInstrument.getId(), Limit.of(1));
            UserInstrumentDto userInstrumentDto = mapToDto(availableInstrument, balanceForTheUserInstrument, swapPerInstrument, mt5Diff);
            result.add(userInstrumentDto);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/users/{telegramId}/instruments/{userInstrumentId}")
    @Transactional(readOnly = true)
    public ApiResponse<UserInstrumentDto> getUserInstruments(@PathVariable Long telegramId, @PathVariable Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        BigDecimal balanceForTheUserInstrument = reportingCalculationRepository.getBalanceForTheUserInstrument(userInstrument.getId(), Limit.of(1));
        BigDecimal swapPerInstrument = orderRepo.getSwapPerInstrument(userInstrument.getId());
        BigDecimal mt5Diff = orderRepo.getMt5DiffPerInstrument(userInstrument.getId());
        UserInstrumentDto userInstrumentDto = mapToDto(userInstrument, balanceForTheUserInstrument, swapPerInstrument, mt5Diff);
        return ApiResponse.success(userInstrumentDto);
    }

    @GetMapping("/users/{telegramId}/terminal-data")
    @Transactional(readOnly = true)
    public ApiResponse<List<TerminalPositionDto>> getMt5Data(@PathVariable Long telegramId) {
        AppUser user = userRepo.findById(telegramId).get();
        List<Mt5ServiceImpl.TradePos> positions = mt5Service.positionsGetAll(user);
        List<TerminalPositionDto> result = positions.stream()
                .map((p) -> {
                    TerminalPositionDto dto = new TerminalPositionDto();
                    dto.setIdentifier(p.getIdentifier());
                    dto.setPriceCurrent(BigDecimal.valueOf(p.getPrice_current()).setScale(5, RoundingMode.HALF_DOWN).doubleValue());
                    dto.setPriceOpen(BigDecimal.valueOf(p.getPrice_open()).setScale(5, RoundingMode.HALF_DOWN).doubleValue());
                    dto.setProfit(BigDecimal.valueOf(p.getProfit()).setScale(2, RoundingMode.HALF_DOWN).doubleValue());
                    dto.setSymbol(p.getSymbol());
                    dto.setTicket(p.getTicket());
                    dto.setTime(p.getTime());
                    dto.setType(p.getType());
                    dto.setVolume(p.getVolume());
                    return dto;
                })
                .toList();
        return ApiResponse.success(result);
    }


    @GetMapping("/users/{telegramId}/terminal-balance")
    @Transactional(readOnly = true)
    public ApiResponse<TerminalBalanceDto> getMt5Balance(@PathVariable Long telegramId) {
        AppUser user = userRepo.findById(telegramId).get();
        Mt5ServiceImpl.BalanceDetails balanceDetails = mt5Service.getAccountInfo(user);

        TerminalBalanceDto dto = new TerminalBalanceDto();
        dto.setCurrency(balanceDetails.getCurrency());
        dto.setBalance(BigDecimal.valueOf(balanceDetails.getBalance()).setScale(2, RoundingMode.HALF_DOWN).doubleValue());
        dto.setEquity(BigDecimal.valueOf(balanceDetails.getEquity()).setScale(2, RoundingMode.HALF_DOWN).doubleValue());
        dto.setAssets(BigDecimal.valueOf(balanceDetails.getAssets()).setScale(2, RoundingMode.HALF_DOWN).doubleValue());
        dto.setCommissionBlocked(BigDecimal.valueOf(balanceDetails.getCommission_blocked()).setScale(2, RoundingMode.HALF_DOWN).doubleValue());

        return ApiResponse.success(dto);
    }

    @GetMapping("/users/{telegramId}/instruments/{userInstrumentId}/unrealized")
    @Transactional(readOnly = true)
    public ApiResponse<UnrealizedProfit> getUnrealizedProfit(@PathVariable Long telegramId, @PathVariable Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();

        if (user.hasAccount()) {
            Instrument instrument = userInstrument.getInstrument();
            Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, user.getSymbolByLabelAndAccType(instrument.getLabel()));
            BigDecimal rate = InstrumentMode.LONG.equals(instrument.getMode())
                    ? BigDecimal.valueOf(infoTick.getBid()).setScale(5, RoundingMode.HALF_DOWN)
                    : BigDecimal.valueOf(infoTick.getAsk()).setScale(5, RoundingMode.HALF_DOWN);
            UnrealizedPnl calculate = unrealizedPnlCalculator.calculate(userInstrumentId, rate);
            UnrealizedPnl calculateHouse = unrealizedPnlCalculator.calculateHouse(userInstrumentId, rate);
            return ApiResponse.success(
                    new UnrealizedProfit(rate,
                            calculate.getResult().doubleValue(),
                            calculateHouse.getResult().doubleValue(),
                            instrument.getLabel())
            );
        } else {
            return ApiResponse.error("User has no account");
        }
    }

/*    @GetMapping("/users/{telegramId}/instruments/{userInstrumentId}/house-unrealized")
    @Transactional(readOnly = true)
    public ApiResponse<UnrealizedProfit> getHouseUnrealizedProfit(@PathVariable Long telegramId, @PathVariable Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();

        if (user.hasAccount()) {
            Instrument instrument = userInstrument.getInstrument();
            Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, user.getSymbolByLabelAndAccType(instrument.getLabel()));
            BigDecimal rate = InstrumentMode.LONG.equals(instrument.getMode())
                    ? BigDecimal.valueOf(infoTick.getBid()).setScale(5, RoundingMode.HALF_DOWN)
                    : BigDecimal.valueOf(infoTick.getAsk()).setScale(5, RoundingMode.HALF_DOWN);
            UnrealizedPnl calculate = unrealizedPnlCalculator.calculateHouse(userInstrumentId, rate);
            return ApiResponse.success(
                    new UnrealizedProfit(rate,
                            calculate.getResult().doubleValue(),
                            instrument.getLabel())
            );
        } else {
            return ApiResponse.error("User has no account");
        }
    }*/

    @GetMapping("/users/{telegramId}/instruments/{userInstrumentId}/house-pnl")
    @Transactional(readOnly = true)
    public ApiResponse<UnrealizedProfit> getHousePnl(@PathVariable Long telegramId, @PathVariable Long userInstrumentId) {
        BigDecimal totalPnl = housePositionConsumptionRepository.getPnlForUserInstrument(userInstrumentId);
        return ApiResponse.success(new UnrealizedProfit(null, totalPnl != null ? totalPnl.doubleValue() : 0, 0.0,""));
    }

    @GetMapping("/users/{telegramId}/orders/{userInstrumentId}")
    @Transactional(readOnly = true)
    public List<OrderDto> userPositions(
            @RequestParam(required = false) String type,
            @PathVariable Long telegramId,
            @PathVariable Long userInstrumentId) {
        List<Order> list = null;
        if ("open".equals(type)) {
            list = orderRepo.findOpenPositions(userInstrumentId);
        } else {
            list = orderRepo.findAllByUserInstrumentIdOrderByCreatedAtDesc(userInstrumentId);
        }

        return list.stream()
                .map(order -> mapToDto(order))
                .toList();
    }

    @GetMapping("/users/{telegramId}/activities")
    @Transactional(readOnly = true)
    public List<UserTelegramActivityDto> userActivities(@PathVariable Long telegramId) {
        List<UserTelegramActivity> allByUserId = userTelegramActivityRepo.findAllByUserIdOrderByIdDesc(telegramId);
        return allByUserId.stream()
                .map(this::mapToDto)
                .toList();
    }

    @GetMapping("/users/{telegramId}/balance-actions")
    @Transactional(readOnly = true)
    public List<UserBalanceActionsDto> userBalanceActions(@PathVariable Long telegramId) {
        List<UserBalanceAction> allByUserId = userBalanceActionsRepository.findAllByUserTelegramIdOrderByDateDesc(telegramId);
        return allByUserId.stream()
                .map(this::mapToDto)
                .toList();
    }

    @GetMapping("/users/{telegramId}/commission-withdrawals")
    @Transactional(readOnly = true)
    public ApiResponse<List<CommissionWithdrawalDto>> commissionWithdrawals(@PathVariable Long telegramId) {
        List<CommissionWithdrawal> allByUserId = commissionWithdrawalRepository.findAllByUserInstrumentUserTelegramIdOrderByDateDesc(telegramId);
        return ApiResponse
                .success(allByUserId.stream()
                .map(this::mapToDto)
                .toList());
    }

    @PostMapping("/users/{telegramId}/commission-withdrawals")
    public CommissionWithdrawalDto createCommissionWithdrawals(@PathVariable Long telegramId,
                                                               @RequestBody CommissionWithdrawalDto payload) {
        validateCommissionWithdrawal(payload);
        CommissionWithdrawal newItem = commissionWithdrawalService.createCommissionWithdrawal(telegramId, payload.getInstrument(), payload.getAmount(), Instant.now());
        return mapToDto(newItem);
    }

    @PostMapping("/users/{telegramId}/balance-actions")
    public UserBalanceActionsDto createUserBalanceActions(@PathVariable Long telegramId,
                                                          @RequestBody UserBalanceActionsDto payload) {
        validateUserBalanceAction(payload);

        UserBalanceAction newItem;
        if (payload.getType().equals(BalanceActionType.DEPOSIT.name())) {
            newItem = userBalanceService.createDepositAction(telegramId, payload.getAmount(), Instant.now());
        } else if (payload.getType().equals(BalanceActionType.WITHDRAWAL.name())) {
            newItem = userBalanceService.createWithdrawAction(telegramId, payload.getAmount(), Instant.now());
        } else {
            throw new IllegalArgumentException("Unsupported balance action type: " + payload.getType());
        }
        return mapToDto(newItem);
    }

    @PostMapping("/users/{telegramId}/instruments/{userInstrumentId}/liquidate")
    public ApiResponse<?> liquidatePosition(@PathVariable Long userInstrumentId) {
        orderOrchestrator.liquidate(userInstrumentId);
        return ApiResponse.success();
    }

    @PostMapping("/users/{telegramId}/instruments/{userInstrumentId}/commission")
    public ApiResponse<?> updateCommission(@PathVariable Long userInstrumentId,
                                           @RequestBody UpdateInstrumentCommissionDto body) {
        userService.updateInstrumentCommission(userInstrumentId, body.getPercentage().divide(AppConstants.HUNDRED, 5, RoundingMode.HALF_DOWN));
        return ApiResponse.success();
    }

    @PostMapping("/users/{telegramId}/instruments/{userInstrumentId}/recalculate-ggl-sheet")
    public ApiResponse<?> recalculateGoogleSheet(@PathVariable Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();
        userInstrument.setLastGoogleSheetSync(null);
        appUserInstrumentRepository.save(userInstrument);

        googleSheetsService.removeSheetForAnInstrument(userInstrumentId);
        googleSheetsService.syncUser(user.getTelegramId());

        return ApiResponse.success();
    }

    @PostMapping("/users/{telegramId}/instruments/{userInstrumentId}/recalculate-inner-ggl-sheet")
    public ApiResponse<?> recalculateReporting(@PathVariable Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();
        userInstrument.setLastGoogleSheetSync(null);
        appUserInstrumentRepository.save(userInstrument);

        reportingCalculationService.recalculateAllForUserInstrument(userInstrumentId);

        return ApiResponse.success();
    }

    @PostMapping("/users/{telegramId}/instruments/update-available-instruments")
    public ApiResponse<?> updateAvailableInstruments(@RequestBody UpdateAvailableInstrumentsDto userInstruments,
                                                     @PathVariable Long telegramId) {
        userService.toggleInstrument(telegramId, AppConstants.EUR_USD, userInstruments.getEurUsd());
        userService.toggleInstrument(telegramId, AppConstants.USD_CAD, userInstruments.getCadUsd());

        return ApiResponse.success("Available instruments updated successfully");
    }

    private void validateCommissionWithdrawal(CommissionWithdrawalDto payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }

        if (payload.getInstrument() == null || payload.getAmount() == null) {
            throw new IllegalArgumentException("Payload type and amount cannot be null");
        }

        if (payload.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }


    private void validateUserBalanceAction(UserBalanceActionsDto payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }

        if (payload.getType() == null || payload.getAmount() == null) {
            throw new IllegalArgumentException("Payload type and amount cannot be null");
        }

        if (payload.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

/*
    @PutMapping("/users/{telegramId}/instruments/{userInstrumentId}/balance-actions/{id}")
    @Transactional
    public UserBalanceActionsDto updateUserBalanceActions(@PathVariable Long id,
                                                          @RequestBody UserBalanceActionsDto payload) {
        UserBalanceAction byId = userBalanceActionsRepository.findById(id).get();
        byId.setType(BalanceActionType.valueOf(payload.getType()));
        byId.setDate(payload.getDate());
        byId.setAmount(payload.getAmount());
        return mapToDto(byId);
    }
*/

    @DeleteMapping("/users/{telegramId}/balance-actions/{id}")
    @Transactional
    public ResponseEntity<?> deleteUserBalanceAction(@PathVariable Long id) {
        userBalanceActionsRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{telegramId}/change-credentials")
    @Transactional
    public ApiResponse<?> changeAccountCreds(@PathVariable Long telegramId,
                                             @RequestBody ChangeAccountCredsDto dto) {

        Integer accountNumber = dto.getAccountNumber();
        String accountPwd = dto.getAccountPassword();

        List<AppUser> users = userRepo.findByMt5AccNumber(accountNumber);
        if (!users.isEmpty()) {
            log.warn("User with the same mt5 account # already exist {}", FormattingTools.formatUser(users.get(0)));
            throw new UserWithSameAccountNumberAlreadyExists();
        }

        if (!mt5Service.checkCredentials(accountNumber, accountPwd)) {
            log.warn("User enters invalid credentials");
            throw new InvalidAccountCredentialsException();
        }
        AccountType accountType = mt5Service.determineAccountType(accountNumber, accountPwd);

        userService.changeGerchikCreds(telegramId, accountNumber, accountPwd, accountType);

        return ApiResponse.success();
    }

    @ExceptionHandler(UserWithSameAccountNumberAlreadyExists.class)
    public ResponseEntity<ApiResponse<?>> handleUserWithSameAccountNumberAlreadyExists() {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Пользователь с таким аккаунтом уже существует"));
    }

    @ExceptionHandler(InvalidAccountCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidAccountCredentialsException() {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Неверные учетные данные аккаунта"));
    }

    private CommissionWithdrawalDto mapToDto(CommissionWithdrawal userBalanceAction) {
        return CommissionWithdrawalDto.builder()
                .id(userBalanceAction.getId())
                .instrument(userBalanceAction.getUserInstrument().getInstrument().getLabel())
                .amount(userBalanceAction.getAmount())
                .date(userBalanceAction.getDate())
                .createdAt(userBalanceAction.getCreatedAt())
                .build();
    }

    private UserBalanceActionsDto mapToDto(UserBalanceAction userBalanceAction) {
        return UserBalanceActionsDto.builder()
                .id(userBalanceAction.getId())
                .type(userBalanceAction.getType().name())
                .amount(userBalanceAction.getAmount())
                .date(userBalanceAction.getDate())
                .createdAt(userBalanceAction.getCreatedAt())
                .build();
    }


    private UserTelegramActivityDto mapToDto(UserTelegramActivity telegramActivity) {
        return UserTelegramActivityDto.builder()
                .id(telegramActivity.getId())
                .userId(telegramActivity.getUserId())
                .type(telegramActivity.getType())
                .data(telegramActivity.getData())
                .currentUserState(telegramActivity.getCurrentUserState())
                .createdAt(telegramActivity.getCreatedAt())
                .build();
    }

    private OrderDto mapToDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .consumptions(order.getConsumptions().stream().map(this::mapToDto).toList())
                .deal(order.getDeal())
                .order(order.getOrder())
                .swap(order.getSwap())
                .commission(order.getMt5Commission())
                .type(order.getType().name())
                .pnlExcel(order.getReportingCalculation() != null ? order.getReportingCalculation().getPnL() : null)
                .systemType(order.getSystemType() != null ? order.getSystemType().name() : null)
                .status(order.getStatus().name())
                .mt5AccNumber(order.getMt5AccNumber())
                .userBaseAmount(order.getUserBaseAmount())
                .userEnteredQuoteAmount(order.getUserEnteredAmount())
                .executedVolume(order.getExecutedVolume())
                .executedBaseAmount(order.getExecutedBaseAmount())
                .remainingQuoteAmount(order.getLifoPosition() != null ? order.getLifoPosition().getRemainingAmount() : BigDecimal.ZERO)
                .houseBaseAmountFrom(order.getHouseBaseAmountFrom())
                .houseBaseAmountTo(order.getHouseBaseAmountTo())
                .clientBaseAmountFrom(order.getClientBaseAmountFrom())
                .clientBaseAmountTo(order.getClientBaseAmountTo())
                .price(order.getPrice())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderConsumptionDto mapToDto(LifoPositionConsumption lifoPositionConsumption) {
        return OrderConsumptionDto.builder()
                .id(lifoPositionConsumption.getId())
                .consumedOrder(mapToDto(lifoPositionConsumption.getConsumedPosition().getOrder()))
                .consumedQuoteAmount(lifoPositionConsumption.getConsumedAmount())
                .leftQuoteAmount(lifoPositionConsumption.getLeftAmount())
                .baseEquivalent(lifoPositionConsumption.getBaseEquivalent())
                .build();
    }


    private UserDto mapToDto(AppUser user) {
        return UserDto.builder()
                .telegramId(user.getTelegramId())
                .username(user.getTelegramUsername())
                .firstName(user.getTelegramFirstName())
                .accountType(user.getAccountType() != null ? user.getAccountType().name() : "")
                .mt5Account(user.getMt5AccNumber())
                .googleSheetId(user.getGoogleSheetId() != null ? "https://docs.google.com/spreadsheets/d/" + user.getGoogleSheetId() : null)
//                .instruments(user.getAvailableInstruments().stream().filter(AppUserInstrument::getActive).map(this::mapToDto).toList())
                .build();
    }

    private UserInstrumentDto mapToDto(AppUserInstrument userInstrument, BigDecimal balance, BigDecimal swapPerInstrument, BigDecimal mt5Diff) {
        return UserInstrumentDto.builder()
                .id(userInstrument.getId())
                .label(userInstrument.getInstrument().getLabel())
                .mode(userInstrument.getInstrument().getMode().name())
                .base(userInstrument.getInstrument().getBase())
                .quote(userInstrument.getInstrument().getQuote())
                .balance(balance)
                .commissionPercentage(userInstrument.getCommissionCoef().multiply(AppConstants.HUNDRED))
                .swap(swapPerInstrument)
                .diffWithMt5(mt5Diff)
                .clientOwnedBaseAmount(userInstrument.getClientOwnedBaseAmount())
                .houseOwnedBaseAmount(userInstrument.getHouseOwnedBaseAmount())
                .clientOwnedQuoteAmount(userInstrument.getClientOwnedQuoteAmount())
                .build();
    }
}
