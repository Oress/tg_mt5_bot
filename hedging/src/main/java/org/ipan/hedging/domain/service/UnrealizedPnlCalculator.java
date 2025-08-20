package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.HouseOwnedPosition;
import org.ipan.hedging.domain.model.LifoPosition;
import org.ipan.hedging.domain.model.repository.AppUserInstrumentRepository;
import org.ipan.hedging.domain.model.repository.HouseOwnedPositionRepository;
import org.ipan.hedging.domain.model.repository.LifoPositionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@AllArgsConstructor
public class UnrealizedPnlCalculator {
    private final LifoPositionRepository lifoPositionRepository;
    private final HouseOwnedPositionRepository houseOwnedPositionRepository;
    private final AppUserInstrumentRepository userInstrumentRepository;

    public UnrealizedPnl calculateHouse(Long userInstrumentId, BigDecimal currentRate) {
        AppUserInstrument userInstrument = userInstrumentRepository.findById(userInstrumentId).get();

        if (AppConstants.USD_CAD.equals(userInstrument.getInstrument().getLabel())) {
            return calculateCadHouse(userInstrumentId, currentRate);
        } else if (AppConstants.EUR_USD.equals(userInstrument.getInstrument().getLabel())) {
            return calculateEurHouse(userInstrumentId, currentRate);
        } else {
            throw new IllegalStateException("Unsupported instrument: " + userInstrument.getInstrument().getLabel());
        }
    }

    public UnrealizedPnl calculate(Long userInstrumentId, BigDecimal currentRate) {
        AppUserInstrument userInstrument = userInstrumentRepository.findById(userInstrumentId).get();

        if (AppConstants.USD_CAD.equals(userInstrument.getInstrument().getLabel())) {
            return calculateCad(userInstrumentId, currentRate);
        } else if (AppConstants.EUR_USD.equals(userInstrument.getInstrument().getLabel())) {
            return calculateEur(userInstrumentId, currentRate);
        } else {
            throw new IllegalStateException("Unsupported instrument: " + userInstrument.getInstrument().getLabel());
        }
    }

    private UnrealizedPnl calculateCad(Long userInstrumentId, BigDecimal currentUsdCadRate) {
        List<LifoPosition> openPositions = lifoPositionRepository.findAllForUnhendgingFifo(userInstrumentId);

        BigDecimal result = BigDecimal.ZERO;

        for (LifoPosition openPosition : openPositions) {
            BigDecimal usdCadRate = openPosition.getOrder().getPrice();
            BigDecimal cadAmount = openPosition.getRemainingAmount();

            BigDecimal unrealizedPnlPerPosition = cadAmount.multiply(
                    BigDecimal.ONE.divide(currentUsdCadRate, 8, RoundingMode.HALF_DOWN)
                            .subtract(BigDecimal.ONE.divide(usdCadRate, 8, RoundingMode.HALF_DOWN)));

            result = result.add(unrealizedPnlPerPosition);
        }

        return new UnrealizedPnl(result);
    }

    private UnrealizedPnl calculateEur(Long userInstrumentId, BigDecimal currentEurUsdRate) {
        List<LifoPosition> openPositions = lifoPositionRepository.findAllForUnhendgingFifo(userInstrumentId);

        BigDecimal result = BigDecimal.ZERO;

        for (LifoPosition openPosition : openPositions) {
            BigDecimal eurUsdRate = openPosition.getOrder().getPrice();
            BigDecimal eurAmount = openPosition.getRemainingAmount();

            BigDecimal unrealizedPnlPerPosition = eurAmount.multiply(currentEurUsdRate.subtract(eurUsdRate));
            result = result.add(unrealizedPnlPerPosition);
        }

        return new UnrealizedPnl(result);
    }

    private UnrealizedPnl calculateCadHouse(Long userInstrumentId, BigDecimal currentUsdCadRate) {
        List<HouseOwnedPosition> openPositions = houseOwnedPositionRepository.findAllForClosingFifo(userInstrumentId);

        BigDecimal result = BigDecimal.ZERO;

        for (HouseOwnedPosition openPosition : openPositions) {
            BigDecimal usdCadRate = openPosition.getOrder().getPrice();
            BigDecimal cadAmount = openPosition.getRemainingAmount();

            BigDecimal unrealizedPnlPerPosition = cadAmount.multiply(
                    BigDecimal.ONE.divide(currentUsdCadRate, 8, RoundingMode.HALF_DOWN)
                            .subtract(BigDecimal.ONE.divide(usdCadRate, 8, RoundingMode.HALF_DOWN)));

            result = switch (openPosition.getType()) {
                case LONG -> result.add(unrealizedPnlPerPosition);
                case SHORT -> result.subtract(unrealizedPnlPerPosition);
            };
        }

        return new UnrealizedPnl(result);
    }

    private UnrealizedPnl calculateEurHouse(Long userInstrumentId, BigDecimal currentEurUsdRate) {
        List<HouseOwnedPosition> openPositions = houseOwnedPositionRepository.findAllForClosingFifo(userInstrumentId);

        BigDecimal result = BigDecimal.ZERO;

        for (HouseOwnedPosition openPosition : openPositions) {
            BigDecimal eurUsdRate = openPosition.getOrder().getPrice();
            BigDecimal eurAmount = openPosition.getRemainingAmount();

            BigDecimal unrealizedPnlPerPosition = eurAmount.multiply(currentEurUsdRate.subtract(eurUsdRate));
            result = result.add(unrealizedPnlPerPosition);
        }

        return new UnrealizedPnl(result);
    }

}
