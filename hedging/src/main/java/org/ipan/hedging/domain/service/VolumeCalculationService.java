package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.HouseOwnedPositionRepository;
import org.ipan.hedging.domain.model.repository.LifoPositionRepository;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class VolumeCalculationService {
    private static final BigDecimal PRECISION_THRESHOLD = new BigDecimal("1e-3"); // or whatever threshold makes sense for your domain
    private final LifoPositionRepository lifoPositionRepository;
    private final HouseOwnedPositionRepository houseOwnedPositionRepository;

    private OrderRepo orderRepo;

    // TODO: verify volume after calculation
    // calculate volume based on userAmount. Use 2 strategies depending on an instrument type:
    public VolumeCalculation calculate(VolumeCalculationParams params) {
        if (params.getInstrument().getMode() == InstrumentMode.SHORT) {
            return new ShortVolumeCalculationStrategy().calculate(params);
        } else if (params.getInstrument().getMode() == InstrumentMode.LONG) {
            return new LongVolumeCalculationStrategy().calculate(params);
        } else {
            throw new IllegalArgumentException("Unsupported instrument type: " + params);
        }
    }

    private interface CalculationStrategy {
        VolumeCalculation calculate(VolumeCalculationParams params);
    }

    // Since it's easy to calculate volume from user amount. I don't need to use lifo stack.
    // I need it for the house positions though.
    // 1 - EUR/USD - simple volume calculation: amount / symbolInfo.getTrade_contract_size(). e.g. 1000 / 100000 = 0.01 lot
    private class ShortVolumeCalculationStrategy implements CalculationStrategy {
        @Override
        public VolumeCalculation calculate(VolumeCalculationParams params) {
            log.info("Short volume calculation for params: {}", params);

            List<PlannedHousePositionConsumption> plannedHousePositionConsumptions = new ArrayList<>();
            CreateHousePosition createHousePosition = null;
            BigDecimal tradeContractSize = BigDecimal.valueOf(params.getTradeContractSize());
            BigDecimal currentTotal = params.getUserInstrument().getTotalOwnedBaseAmount();
            BigDecimal newTotalBaseAmount = null;
            BigDecimal operationUserAmountBase = null;
            BigDecimal tempClientBaseAmount = null;
            BigDecimal newUserOwnedBase = null;
            Double price = null;

            // HEDGE IN
            if (!params.getIsBuy()) {
                price = params.getTick().getAsk();
                operationUserAmountBase = BigDecimal.valueOf(params.getUserAmount());

                // e.g. 3600 or 1400
                tempClientBaseAmount = params.getUserInstrument().getClientOwnedBaseAmount().add(operationUserAmountBase);

                // e.g. 4000 or 1000
                newTotalBaseAmount = tempClientBaseAmount.setScale(-3, RoundingMode.HALF_UP);
            } else {
                operationUserAmountBase = BigDecimal.valueOf(params.getUserAmount());
                if (params.getUserInstrument().getClientOwnedBaseAmount().compareTo(operationUserAmountBase) < 0) {
                    log.error("Insufficient client owned BASE amount: {} for execution base amount: {}", params.getUserInstrument().getClientOwnedBaseAmount(), operationUserAmountBase);
                    throw new IllegalArgumentException("Insufficient client owned base amount for the operation");
                }

                // HEDGE OUT
                price = params.getTick().getBid();

                tempClientBaseAmount = BigDecimal.ZERO.max(params.getUserInstrument().getClientOwnedBaseAmount().subtract(operationUserAmountBase));

                // due to the loss of precision, tempClientBaseAmount may be something like 1e-8
                if (tempClientBaseAmount.compareTo(PRECISION_THRESHOLD) < 0) {
                    tempClientBaseAmount = BigDecimal.ZERO;
                }

                // round to the nearest 1000
                newTotalBaseAmount = tempClientBaseAmount.setScale(-3, RoundingMode.HALF_UP);
            }

            BigDecimal executionBaseAmount = newTotalBaseAmount.subtract(currentTotal).abs();
            BigDecimal executionVolume = executionBaseAmount.divide(tradeContractSize, 8, RoundingMode.HALF_DOWN).abs();
            newUserOwnedBase = tempClientBaseAmount;
            BigDecimal oldHouseOwnedBase = params.getUserInstrument().getHouseOwnedBaseAmount();
            BigDecimal newHouseOwnedBase = newTotalBaseAmount.subtract(tempClientBaseAmount);

            // close all positions
            // 300 -> 0
            // -300 -> 0
            if (newHouseOwnedBase.compareTo(BigDecimal.ZERO) == 0) {
                closeAllHousePositions(params, plannedHousePositionConsumptions);
            }
            // opposite amount -> close all positions and open a new one
            // 300 -> -320
            // -300 -> 320
            else if (newHouseOwnedBase.multiply(oldHouseOwnedBase).compareTo(BigDecimal.ZERO) < 0) {
                closeAllHousePositions(params, plannedHousePositionConsumptions);
                createHousePosition = new CreateHousePosition(newHouseOwnedBase);
            }
            // increase in absolute value -> create position
            // 300 -> 320
            // -300 -> -320
            else if (newHouseOwnedBase.abs().compareTo(oldHouseOwnedBase.abs()) > 0) {
                createHousePosition = new CreateHousePosition(newHouseOwnedBase.subtract(oldHouseOwnedBase));
            }
            // decrease in absolute value -> close position partially
            // 320 -> 300
            // -320 -> -300
            else if (newHouseOwnedBase.abs().compareTo(oldHouseOwnedBase.abs()) < 0) {
                // e.g. 20
                BigDecimal amountLeft = newHouseOwnedBase.subtract(oldHouseOwnedBase).abs();
                List<HouseOwnedPosition> allForClosing = houseOwnedPositionRepository.findAllForClosingFifo(params.getUserInstrument().getId());
                for (int i = 0; amountLeft.compareTo(BigDecimal.ZERO) > 0; i++) {
                    HouseOwnedPosition positionToConsume = allForClosing.get(i);
                    BigDecimal consumptionAmount = positionToConsume.getRemainingAmount().min(amountLeft);
                    amountLeft = amountLeft.subtract(consumptionAmount);
                    plannedHousePositionConsumptions.add(new PlannedHousePositionConsumption(positionToConsume.getId(), consumptionAmount));
                }
            } else  {
                // ????
                log.error("Unexpected case for newHouseOwnedBase: {}, oldHouseOwnedBase: {}", newHouseOwnedBase, oldHouseOwnedBase);
            }

            return VolumeCalculation.builder()
                    .oldUserOwnedBase(params.getUserInstrument().getClientOwnedBaseAmount())
                    .oldHouseOwnedBase(oldHouseOwnedBase)
                    .newUserOwnedBase(newUserOwnedBase)
                    .newHouseOwnedBase(newHouseOwnedBase)
                    .originalAmount(params.getUserAmount())
                    .executionUserOwnedBase(operationUserAmountBase)
                    .executionVolume(executionVolume)
                    .executionBase(executionBaseAmount)
                    .price(BigDecimal.valueOf(price))
                    .lifoPositionsForConsumption(Collections.emptyList())
                    .housePositionsForConsumption(plannedHousePositionConsumptions)
                    .createHousePosition(createHousePosition)
                    .build();
        }
    }

    // 2 - USD/CAD(inverse hedging):
    //      client_part = amount / symbolInfo.getTrade_contract_size()
    //      total_part = round_half_down(client_part)
    //      house_part = total_part - client_part
    // e.g. 1000 CAD @ 1.36 ~ 735.29 USD (0.007 lot) - client part. 0.01 lot - total part, 0.003 lot (in USD) - house part
    private class LongVolumeCalculationStrategy implements CalculationStrategy {
        @Override
        public VolumeCalculation calculate(VolumeCalculationParams params) {
            List<PlannedLifoPositionConsumption> plannedLifoPositionConsumptions = new ArrayList<>();
            List<PlannedHousePositionConsumption> plannedHousePositionConsumptions = new ArrayList<>();
            CreateHousePosition createHousePosition = null;
            BigDecimal tradeContractSize = BigDecimal.valueOf(params.getTradeContractSize());
            BigDecimal currentTotal = params.getUserInstrument().getTotalOwnedBaseAmount();
            BigDecimal newTotalBaseAmount = null;
            BigDecimal operationUserAmountBase = null;
            BigDecimal tempClientBaseAmount = null;
            BigDecimal newUserOwnedBase = null;
            Double price = null;

            // HEDGE IN
            if (params.getIsBuy()) {
                price = params.getTick().getAsk();
                BigDecimal operationAmountQuote = BigDecimal.valueOf(params.getUserAmount());
                operationUserAmountBase = operationAmountQuote.divide(BigDecimal.valueOf(price), 8, RoundingMode.HALF_DOWN);

                // e.g. 3600 or 1400
//                tempClientBaseAmount = params.getUserInstrument().getActualClientOwnedBaseAmount().add(operationUserAmountBase);
                tempClientBaseAmount = params.getUserInstrument().getClientOwnedBaseAmount().add(operationUserAmountBase);

                // e.g. 4000 or 1000
                newTotalBaseAmount = tempClientBaseAmount.setScale(-3, RoundingMode.HALF_UP);
            } else {
                BigDecimal userAmountBd = BigDecimal.valueOf(params.getUserAmount());
                if (params.getUserInstrument().getClientOwnedQuoteAmount().compareTo(userAmountBd) < 0) {
                    log.error("Insufficient client owned QUOTE amount: {} for execution base amount: {}", params.getUserInstrument().getClientOwnedBaseAmount(), userAmountBd);
                    throw new IllegalArgumentException("Insufficient client owned base amount for the operation");
                }

                // HEDGE OUT
                // find all open orders with non-closed positions (left to unhedge > 0)
                // create 'consumption' records, i.e. which orders are used to unhedge and how much is used from each order.

                // this is the base amount of consumed orders. i.e. per order price / consumed quote amount
                // e.g. 1000 / 1.36 = 735.29 USD add to the operationUserAmountBase
                operationUserAmountBase = BigDecimal.ZERO;
                price = params.getTick().getBid();


                List<LifoPosition> allForUnhedging = lifoPositionRepository.findAllForUnhendgingFifo(params.getUserInstrument().getId());
                Integer amountLeft = params.getUserAmount();
                for (int i = 0; amountLeft > 0; i++) {
                    LifoPosition positionToConsume = allForUnhedging.get(i);
                    BigDecimal quoteConsumptionAmount = positionToConsume.getRemainingAmount().min(BigDecimal.valueOf(amountLeft));
                    amountLeft -= quoteConsumptionAmount.intValue();

                    operationUserAmountBase = operationUserAmountBase.add(quoteConsumptionAmount.divide(positionToConsume.getOrder().getPrice(), 8, RoundingMode.HALF_DOWN));
                    plannedLifoPositionConsumptions.add(new PlannedLifoPositionConsumption(positionToConsume.getId(), quoteConsumptionAmount));
                }

                // should be >= 0
                tempClientBaseAmount = BigDecimal.ZERO.max(params.getUserInstrument().getClientOwnedBaseAmount().subtract(operationUserAmountBase));

                // due to the loss of precision, tempClientBaseAmount may be something like 1e-8
                if (tempClientBaseAmount.compareTo(PRECISION_THRESHOLD) < 0) {
                    tempClientBaseAmount = BigDecimal.ZERO;
                }

                // round to the nearest 1000
                newTotalBaseAmount = tempClientBaseAmount.setScale(-3, RoundingMode.HALF_UP);
            }

            BigDecimal executionBaseAmount = newTotalBaseAmount.subtract(currentTotal).abs();
            BigDecimal executionVolume = executionBaseAmount.divide(tradeContractSize, 8, RoundingMode.HALF_DOWN).abs();
            newUserOwnedBase = tempClientBaseAmount;
            BigDecimal oldHouseOwnedBase = params.getUserInstrument().getHouseOwnedBaseAmount();
            BigDecimal newHouseOwnedBase = newTotalBaseAmount.subtract(tempClientBaseAmount);

            // close all positions
            // 300 -> 0
            // -300 -> 0
            if (newHouseOwnedBase.compareTo(BigDecimal.ZERO) == 0) {
                closeAllHousePositions(params, plannedHousePositionConsumptions);
            }
            // opposite amount -> close all positions and open a new one
            // 300 -> -320
            // -300 -> 320
            else if (newHouseOwnedBase.multiply(oldHouseOwnedBase).compareTo(BigDecimal.ZERO) < 0) {
                closeAllHousePositions(params, plannedHousePositionConsumptions);
                createHousePosition = new CreateHousePosition(newHouseOwnedBase);
            }
            // increase in absolute value -> create position
            // 300 -> 320
            // -300 -> -320
            else if (newHouseOwnedBase.abs().compareTo(oldHouseOwnedBase.abs()) > 0) {
                createHousePosition = new CreateHousePosition(newHouseOwnedBase.subtract(oldHouseOwnedBase));
            }
            // decrease in absolute value -> close position partially
            // 320 -> 300
            // -320 -> -300
            else if (newHouseOwnedBase.abs().compareTo(oldHouseOwnedBase.abs()) < 0) {
                // e.g. 20
                BigDecimal amountLeft = newHouseOwnedBase.subtract(oldHouseOwnedBase).abs();
                List<HouseOwnedPosition> allForClosing = houseOwnedPositionRepository.findAllForClosingFifo(params.getUserInstrument().getId());
                for (int i = 0; amountLeft.compareTo(BigDecimal.ZERO) > 0; i++) {
                    HouseOwnedPosition positionToConsume = allForClosing.get(i);
                    BigDecimal consumptionAmount = positionToConsume.getRemainingAmount().min(amountLeft);
                    amountLeft = amountLeft.subtract(consumptionAmount);
                    plannedHousePositionConsumptions.add(new PlannedHousePositionConsumption(positionToConsume.getId(), consumptionAmount));
                }
            } else  {
                // ????
                log.error("Unexpected case for newHouseOwnedBase: {}, oldHouseOwnedBase: {}", newHouseOwnedBase, oldHouseOwnedBase);
            }

            // TODO: check the scale of the newUserOwnedBase and newTotalBaseAmount.subtract(tempClientBaseAmount), round to 2?
            //  Can there be some small amounts like 0.00001?
            return VolumeCalculation.builder()
                    .oldUserOwnedBase(params.getUserInstrument().getClientOwnedBaseAmount())
                    .oldHouseOwnedBase(oldHouseOwnedBase)
                    .newUserOwnedBase(newUserOwnedBase)
                    .newHouseOwnedBase(newHouseOwnedBase)
                    .originalAmount(params.getUserAmount())
                    .executionUserOwnedBase(operationUserAmountBase)
                    .executionVolume(executionVolume)
                    .executionBase(executionBaseAmount)
                    .price(BigDecimal.valueOf(price))
                    .lifoPositionsForConsumption(plannedLifoPositionConsumptions)
                    .housePositionsForConsumption(plannedHousePositionConsumptions)
                    .createHousePosition(createHousePosition)
                    .build();
        }

    }

    private void closeAllHousePositions(VolumeCalculationParams params, List<PlannedHousePositionConsumption> plannedHousePositionConsumptions) {
        List<HouseOwnedPosition> allForClosing = houseOwnedPositionRepository.findAllForClosingFifo(params.getUserInstrument().getId());
        for (HouseOwnedPosition houseOwnedPosition : allForClosing) {
            plannedHousePositionConsumptions.add(new PlannedHousePositionConsumption(houseOwnedPosition.getId(), houseOwnedPosition.getRemainingAmount()));
        }
    }
}
