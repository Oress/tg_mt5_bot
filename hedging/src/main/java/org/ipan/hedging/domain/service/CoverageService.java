package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import org.ipan.hedging.application.service.Mt5Service;
import org.ipan.hedging.application.service.Mt5ServiceImpl;
import org.ipan.hedging.application.service.TerminalBroker;
import org.ipan.hedging.application.service.command.PlaceOrderCommand;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.domain.model.InstrumentMode;
import org.ipan.hedging.domain.model.repository.AppUserInstrumentRepository;
import org.ipan.hedging.domain.model.repository.ReportingCalculationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
@AllArgsConstructor
public class CoverageService {
    private static final BigDecimal COMFORTABLE = new BigDecimal("0.08");
    private static final BigDecimal WARNING = new BigDecimal("0.06");
    private static final BigDecimal RISKY = new BigDecimal("0.04");

    private final AppUserInstrumentRepository userInstrumentRepository;
    private final ReportingCalculationRepository calculationRepository;
    private final UnrealizedPnlCalculator unrealizedPnlCalculator;
    private final TerminalBroker terminalBroker;


    @Transactional(readOnly = true)
    public CoverageReport assessHedgeOperation(Long userInstrumentId, PlaceOrderCommand command) {
        AppUserInstrument userInstrument = userInstrumentRepository.findById(userInstrumentId).get();
        AppUser user = userInstrument.getUser();
        Instrument instrument = userInstrument.getInstrument();

        if ((InstrumentMode.LONG.equals(instrument.getMode()) && !command.isBuy()) || (InstrumentMode.SHORT.equals(instrument.getMode()) && command.isBuy())) {
            return new CoverageReport(CoverageRisk.COMFORTABLE, BigDecimal.ONE);
        }

        Set<AppUserInstrument> availableInstruments = user.getAvailableInstruments();
        BigDecimal exposure = calculateExposure(availableInstruments, command);
        BigDecimal equity = calculateEquity(user, availableInstruments, command);

        BigDecimal newCoverage = equity
                .divide(exposure.add(new BigDecimal("0.000001")), 5, RoundingMode.HALF_DOWN);

        CoverageRisk risk = CoverageRisk.COMFORTABLE;
        if (newCoverage.compareTo(RISKY) < 0) {
            risk = CoverageRisk.RISKY;
        } else if (newCoverage.compareTo(WARNING) < 0) {
            risk = CoverageRisk.WARN;
        }

        return new CoverageReport(risk, newCoverage);
    }

    private BigDecimal calculateEquity(AppUser user, Set<AppUserInstrument> availableInstruments, PlaceOrderCommand command) {
        BigDecimal result = user.getBalanceUsd();

        for (AppUserInstrument userInstrument : availableInstruments) {
            Instrument instrument = userInstrument.getInstrument();
            boolean isLong = InstrumentMode.LONG.equals(instrument.getMode());
            String symbol = user.getSymbolByLabelAndAccType(instrument.getLabel());
            Mt5Service mt5Service = terminalBroker.getMt5Service(user);
            Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, symbol);
            BigDecimal currentRate = isLong
                    ? BigDecimal.valueOf(infoTick.getBid()).setScale(5, RoundingMode.HALF_DOWN)
                    : BigDecimal.valueOf(infoTick.getAsk()).setScale(5, RoundingMode.HALF_DOWN);

            BigDecimal totalPnlForUserInstrument = calculationRepository.getTotalPnlForUserInstrument(userInstrument.getId());
            totalPnlForUserInstrument = totalPnlForUserInstrument == null ? BigDecimal.ZERO : totalPnlForUserInstrument;
            UnrealizedPnl calculate = unrealizedPnlCalculator.calculate(userInstrument.getId(), currentRate);
            result = result.add(totalPnlForUserInstrument).add(calculate.getResult());
        }

        return result;

    }

    private BigDecimal calculateExposure(Set<AppUserInstrument> availableInstruments, PlaceOrderCommand command) {
        BigDecimal result = BigDecimal.ZERO;

        for (AppUserInstrument userInstrument : availableInstruments) {
            AppUser user = userInstrument.getUser();
            Instrument instrument = userInstrument.getInstrument();
            boolean isLong = InstrumentMode.LONG.equals(instrument.getMode());
            String symbol = user.getSymbolByLabelAndAccType(instrument.getLabel());
            Mt5Service mt5Service = terminalBroker.getMt5Service(user);
            Mt5ServiceImpl.InfoTick infoTick = mt5Service.getInfoTick(user, symbol);
            BigDecimal currentRate = isLong
                    ? BigDecimal.valueOf(infoTick.getBid()).setScale(5, RoundingMode.HALF_DOWN)
                    : BigDecimal.valueOf(infoTick.getAsk()).setScale(5, RoundingMode.HALF_DOWN);

            if (isLong) {
                // e.g. USD/CAD
                BigDecimal quoteAmount = userInstrument.getClientOwnedQuoteAmount();
                result = result.add(quoteAmount.divide(currentRate, 5, RoundingMode.HALF_DOWN));

                if (command.getInstrumentLabel().equals(instrument.getLabel())) {
                    if (command.isBuy()) {
                        result = result.add(BigDecimal.valueOf(command.getUserAmount()).divide(currentRate, 5, RoundingMode.HALF_DOWN));
                    } else {
                        result = result.subtract(BigDecimal.valueOf(command.getUserAmount()).divide(currentRate, 5, RoundingMode.HALF_DOWN));
                    }
                }
            } else {
                // e.g. EUR/USD
                BigDecimal baseAmount = userInstrument.getClientOwnedBaseAmount();
                result = result.add(baseAmount.multiply(currentRate).setScale(5, RoundingMode.HALF_DOWN));

                if (command.getInstrumentLabel().equals(instrument.getLabel())) {
                    if (!command.isBuy()) {
                        result = result.add(BigDecimal.valueOf(command.getUserAmount()).multiply(currentRate));
                    } else {
                        result = result.subtract(BigDecimal.valueOf(command.getUserAmount()).multiply(currentRate));
                    }
                }
            }
        }

        return result;
    }
}
