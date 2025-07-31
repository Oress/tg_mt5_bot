package org.ipan.hedging.controller;

import lombok.AllArgsConstructor;
import org.ipan.hedging.application.service.GoogleSheetsService;
import org.ipan.hedging.controller.dto.*;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class DashboardController {
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;
    private final UserTelegramActivityRepository userTelegramActivityRepo;
    private final UserBalanceActionsRepository userBalanceActionsRepository;
    private final AppUserInstrumentRepository appUserInstrumentRepository;
    private final GoogleSheetsService googleSheetsService;

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

    @GetMapping("/users/{telegramId}/orders/{positionId}")
    @Transactional(readOnly = true)
    public List<OrderDto> userPositions(
            @RequestParam(required = false) String type,
            @PathVariable Long telegramId,
            @PathVariable Long positionId) {
        List<Order> list = null;
        if ("open".equals(type)) {
            list = orderRepo.findOpenPositions(positionId);
        } else {
            list = orderRepo.findAllByUserInstrumentIdOrderByCreatedAtDesc(positionId);
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
        AppUserInstrument userInstrument = appUserInstrumentRepository.getReferenceById(userInstrumentId);

        validateUserBalanceAction(payload);

        UserBalanceAction newItem = new UserBalanceAction();
        newItem.setType(BalanceActionType.valueOf(payload.getType()));
//        newItem.setDate(payload.getDate());
        newItem.setDate(Instant.now());
        newItem.setAmount(payload.getAmount());
        newItem.setUserInstrument(userInstrument);

        newItem = userBalanceActionsRepository.save(newItem);

        googleSheetsService.syncUser(userInstrument.getUser().getTelegramId());
        return mapToDto(newItem);
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
                .quoteAmountLeftToUnhedge(order.getQuoteAmountLeftToUnhedge())
                .houseBaseAmountFrom(order.getHouseBaseAmountFrom())
                .houseBaseAmountTo(order.getHouseBaseAmountTo())
                .clientBaseAmountFrom(order.getClientBaseAmountFrom())
                .clientBaseAmountTo(order.getClientBaseAmountTo())
                .price(order.getPrice())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderConsumptionDto mapToDto(OrderConsumption orderConsumption) {
        return OrderConsumptionDto.builder()
                .id(orderConsumption.getId())
                .consumedOrder(mapToDto(orderConsumption.getConsumedOrder()))
                .consumedQuoteAmount(orderConsumption.getConsumedQuoteAmount())
                .leftQuoteAmount(orderConsumption.getLeftQuoteAmount())
                .baseEquivalent(orderConsumption.getBaseEquivalent())
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
                .instruments(user.getAvailableInstruments().stream().filter(AppUserInstrument::getActive).map(this::mapToDto).toList())
                .build();
    }

    private UserInstrumentDto mapToDto(AppUserInstrument userInstrument) {
        return UserInstrumentDto.builder()
                .id(userInstrument.getId())
                .label(userInstrument.getInstrument().getLabel())
                .mode(userInstrument.getInstrument().getMode().name())
                .base(userInstrument.getInstrument().getBase())
                .quote(userInstrument.getInstrument().getQuote())
                .clientOwnedBaseAmount(userInstrument.getClientOwnedBaseAmount())
                .houseOwnedBaseAmount(userInstrument.getHouseOwnedBaseAmount())
                .clientOwnedQuoteAmount(userInstrument.getClientOwnedQuoteAmount())
                .build();
    }
}
