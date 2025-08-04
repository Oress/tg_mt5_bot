package org.ipan.hedging.controller;

import lombok.AllArgsConstructor;
import org.ipan.hedging.Config;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.application.service.UserBalanceService;
import org.ipan.hedging.controller.dto.*;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.*;
import org.ipan.hedging.domain.service.UnrealizedPnl;
import org.ipan.hedging.domain.service.UnrealizedPnlCalculator;
import org.ipan.hedging.domain.service.UserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
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
                               UnrealizedPnlCalculator unrealizedPnlCalculator) {
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
        for (AppUserInstrument availableInstrument : byId.getAvailableInstruments()) {
            BigDecimal balanceForTheUserInstrument = reportingCalculationRepository.getBalanceForTheUserInstrument(availableInstrument.getId(), Limit.of(1));
            UserInstrumentDto userInstrumentDto = mapToDto(availableInstrument, balanceForTheUserInstrument);
            result.add(userInstrumentDto);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/users/{telegramId}/instruments/{userInstrumentId}")
    @Transactional(readOnly = true)
    public ApiResponse<UserInstrumentDto> getUserInstruments(@PathVariable Long telegramId, @PathVariable Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        BigDecimal balanceForTheUserInstrument = reportingCalculationRepository.getBalanceForTheUserInstrument(userInstrument.getId(), Limit.of(1));
        UserInstrumentDto userInstrumentDto = mapToDto(userInstrument, balanceForTheUserInstrument);
        return ApiResponse.success(userInstrumentDto);
    }

    @GetMapping("/users/{telegramId}/instruments/{userInstrumentId}/unrealized")
    @Transactional(readOnly = true)
    public ApiResponse<UnrealizedProfit> getUnrealizedProfit(@PathVariable Long telegramId, @PathVariable Long userInstrumentId) {
        AppUserInstrument userInstrument = appUserInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();
        Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, user.getSymbolByLabelAndAccType(userInstrument.getInstrument().getLabel()));
        BigDecimal rate = BigDecimal.valueOf(infoTick.getBid());
        UnrealizedPnl calculate = unrealizedPnlCalculator.calculate(userInstrumentId, rate);
        return ApiResponse.success(
                new UnrealizedProfit(infoTick.getBid(),
                calculate.getResult().doubleValue(),
                userInstrument.getInstrument().getLabel())
        );
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

    @GetMapping("/users/{telegramId}/instruments/{userInstrumentId}/balance-actions")
    @Transactional(readOnly = true)
    public List<UserBalanceActionsDto> userBalanceActions(@PathVariable Long userInstrumentId) {
        List<UserBalanceAction> allByUserId = userBalanceActionsRepository.findByUserInstrumentIdOrderByDateDesc(userInstrumentId);
        return allByUserId.stream()
                .map(this::mapToDto)
                .toList();
    }

    @PostMapping("/users/{telegramId}/instruments/{userInstrumentId}/balance-actions")
    public UserBalanceActionsDto createUserBalanceActions(@PathVariable Long userInstrumentId,
                                                          @RequestBody UserBalanceActionsDto payload) {
        validateUserBalanceAction(payload);

        UserBalanceAction newItem;
        if (payload.getType().equals(BalanceActionType.DEPOSIT.name())) {
            newItem = userBalanceService.createDepositAction(userInstrumentId, payload.getAmount(), Instant.now());
        } else if (payload.getType().equals(BalanceActionType.WITHDRAWAL.name())) {
            newItem = userBalanceService.createWithdrawAction(userInstrumentId, payload.getAmount(), Instant.now());
        } else {
            throw new IllegalArgumentException("Unsupported balance action type: " + payload.getType());
        }
        return mapToDto(newItem);
    }

    @PostMapping("/users/{telegramId}/instruments/update-available-instruments")
    public ApiResponse<?> updateAvailableInstruments(@RequestBody UpdateAvailableInstrumentsDto userInstruments,
                                                     @PathVariable Long telegramId) {
        userService.toggleInstrument(telegramId, AppConstants.EUR_USD, userInstruments.getEurUsd());
        userService.toggleInstrument(telegramId, AppConstants.USD_CAD, userInstruments.getCadUsd());

        return ApiResponse.success("Available instruments updated successfully");
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

    @DeleteMapping("/users/{telegramId}/instruments/{userInstrumentId}/balance-actions/{id}")
    @Transactional
    public ResponseEntity<?> deleteUserBalanceAction(@PathVariable Long id) {
        userBalanceActionsRepository.deleteById(id);
        return ResponseEntity.ok().build();
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
                .type(order.getType().name())
                .status(order.getStatus().name())
                .mt5AccNumber(order.getMt5AccNumber())
                .userBaseAmount(order.getUserBaseAmount())
                .userEnteredQuoteAmount(order.getUserEnteredQuoteAmount())
                .executedVolume(order.getExecutedVolume())
                .executedBaseAmount(order.getExecutedBaseAmount())
                .remainingQuoteAmount(order.getLifoPosition() != null ? order.getLifoPosition().getRemainingQuoteAmount() : BigDecimal.ZERO)
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
                .consumedQuoteAmount(lifoPositionConsumption.getConsumedQuoteAmount())
                .leftQuoteAmount(lifoPositionConsumption.getLeftQuoteAmount())
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

    private UserInstrumentDto mapToDto(AppUserInstrument userInstrument, BigDecimal balance) {
        return UserInstrumentDto.builder()
                .id(userInstrument.getId())
                .label(userInstrument.getInstrument().getLabel())
                .mode(userInstrument.getInstrument().getMode().name())
                .base(userInstrument.getInstrument().getBase())
                .quote(userInstrument.getInstrument().getQuote())
                .balance(balance)
                .clientOwnedBaseAmount(userInstrument.getClientOwnedBaseAmount())
                .houseOwnedBaseAmount(userInstrument.getHouseOwnedBaseAmount())
                .clientOwnedQuoteAmount(userInstrument.getClientOwnedQuoteAmount())
                .build();
    }
}
