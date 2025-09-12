package org.ipan.hedging.application.service;

import org.ipan.hedging.domain.model.AccountType;
import org.ipan.hedging.domain.model.AppUser;

import java.util.List;

public interface Mt5Service {
    Boolean checkCredentials(Integer accountNumber, String password);
    Mt5ServiceImpl.SymbolInfo symbolInfo(AppUser user, String symbol);
    AccountType determineAccountType(Integer accountNumber, String password);
    Mt5ServiceImpl.TradePos positionsGet(AppUser user, String symbol);
    Mt5ServiceImpl.BalanceDetails getAccountInfo(AppUser user);
    List<Mt5ServiceImpl.TradePos> positionsGetAll(AppUser user);
    Double getPriceForShort(AppUser user, String symbol);
    Mt5ServiceImpl.InfoTick getInfoTick(AppUser user, String symbol);
    Mt5ServiceImpl.OrderDetails placeLongOrder(AppUser user, Long ticket, String symbol, Double volume, String correlationId);
    Mt5ServiceImpl.OrderDetails placeShortOrder(AppUser user, Long ticket, String symbol, Double volume, String correlationId);
    List<Mt5ServiceImpl.TradeDeal> getOrderHistory(AppUser user, Long ticket, Long positionId);
}
