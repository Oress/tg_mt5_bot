package org.ipan.hedging.controller;

import lombok.AllArgsConstructor;
import org.ipan.hedging.controller.dto.OrderConsumptionDto;
import org.ipan.hedging.controller.dto.OrderDto;
import org.ipan.hedging.controller.dto.UserDto;
import org.ipan.hedging.controller.dto.UserInstrumentDto;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.OrderConsumption;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class DashboardController {
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;

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
//        if ("open".equals(type)) {
//
//        }

        List<Order> list = orderRepo.findAllByUserInstrumentIdOrderByCreatedAtDesc(positionId);

        return list.stream()
                .map(order -> mapToDto(order))
                .toList();
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
                .baseEquivalent(orderConsumption.getBaseEquivalent())
                .build();
    }


    private UserDto mapToDto(AppUser user) {
        return UserDto.builder()
                .telegramId(user.getTelegramId())
                .username(user.getTelegramUsername())
                .firstName(user.getTelegramFirstName())
                .accountType(user.getAccountType().name())
                .mt5Account(user.getMt5AccNumber())
                .googleSheetId(user.getGoogleSheetId())
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
