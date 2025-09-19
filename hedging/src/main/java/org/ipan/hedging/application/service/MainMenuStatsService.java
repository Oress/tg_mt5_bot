package org.ipan.hedging.application.service;

import org.ipan.hedging.Config;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.ReportingCalculationRepository;
import org.ipan.hedging.domain.service.UnrealizedPnl;
import org.ipan.hedging.domain.service.UnrealizedPnlCalculator;
import org.ipan.hedging.telegram.TelegramMessages;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
public class MainMenuStatsService {
    private final ReportingCalculationRepository reportingCalculationRepository;
    private final UnrealizedPnlCalculator unrealizedPnlCalculator;
    private final Mt5Service mt5Service;

    public MainMenuStatsService(
            ReportingCalculationRepository reportingCalculationRepository,
            UnrealizedPnlCalculator unrealizedPnlCalculator,
            @Qualifier(Config.AUX_MT5) Mt5Service mt5Service) {
        this.reportingCalculationRepository = reportingCalculationRepository;
        this.unrealizedPnlCalculator = unrealizedPnlCalculator;
        this.mt5Service = mt5Service;
    }

    @Transactional(readOnly = true)
    public TelegramMessages.MainMenuPayload createPayload(AppUser user) {
        TelegramMessages.MainMenuPayload mainMenuPayload = new TelegramMessages.MainMenuPayload();

        mainMenuPayload.setUserBalance(user.getBalanceUsd());

        Set<AppUserInstrument> availableInstruments = user.getAvailableInstruments();
        for (AppUserInstrument userInstrument : availableInstruments) {
            Instrument instrument = userInstrument.getInstrument();
            ReportingCalculation lastRow = reportingCalculationRepository.getLastRowForTheUserInstrument(userInstrument.getId(), Limit.of(1));

            if (lastRow != null) {
                Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, user.getSymbolByLabelAndAccType(instrument.getLabel()));
                BigDecimal currentRate = InstrumentMode.LONG.equals(instrument.getMode())
                        ? BigDecimal.valueOf(infoTick.getBid()).setScale(5, RoundingMode.HALF_DOWN)
                        : BigDecimal.valueOf(infoTick.getAsk()).setScale(5, RoundingMode.HALF_DOWN);

                UnrealizedPnl calculate = unrealizedPnlCalculator.calculate(userInstrument.getId(), currentRate);
                if (AppConstants.USD_CAD.equals(instrument.getLabel())) {
                    mainMenuPayload.setCadBalance(lastRow.getBalance());
                    mainMenuPayload.setCadUnrealizedPnl(calculate.getResult());

                } else if (AppConstants.EUR_USD.equals(instrument.getLabel())) {
                    mainMenuPayload.setEurBalance(lastRow.getBalance());
                    mainMenuPayload.setEurUnrealizedPnl(calculate.getResult());
                }
            }
        }


        return mainMenuPayload;
    }
}
