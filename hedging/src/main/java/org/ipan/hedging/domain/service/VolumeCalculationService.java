package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.*;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class VolumeCalculationService {
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

    // 1 - EUR/USD - simple volume calculation: amount / symbolInfo.getTrade_contract_size(). e.g. 1000 / 100000 = 0.01 lot
    private class ShortVolumeCalculationStrategy implements CalculationStrategy {
        @Override
        public VolumeCalculation calculate(VolumeCalculationParams params) {
            BigDecimal userAmountDecimal = BigDecimal.valueOf(params.getUserAmount());

            BigDecimal volume = userAmountDecimal
                    .divide(BigDecimal.valueOf(params.getTradeContractSize()), 5, BigDecimal.ROUND_DOWN);
            return VolumeCalculation.builder()
                    .originalAmount(params.getUserAmount())
//                    .userOwnedVolume(volume)
                    .executionUserOwnedBase(userAmountDecimal)
                    .executionVolume(volume)
                    .executionBase(userAmountDecimal)
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
            // TODO: When hedging OUT USD/CAD, we should make a decision based on the expected value of the house base value
            //  House base value = min()

            // TODO: Main goal House amount -> min.
            //  It will be less than 500 USD.
            //  House amount = min (|floor(new_client_base_amount) - new_client_base_amount|, |ceil(new_client_base_amount) - new_client_base_amount)|)
            //  this is to ensure that the house amount is as close to zero as possible
            //  new_client_base_amount will be either floor(new_client_base_amount) or ceil(new_client_base_amount) depending which one gives smaller house amount.
            //  2) How to find new_client_base_amount?
            //  temp_client_base_amount = total_base_amount + operation_client_base_amount



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
                operationUserAmountBase = operationAmountQuote.divide(BigDecimal.valueOf(price), 5, RoundingMode.HALF_DOWN);

                // e.g. 3600 or 1400
                tempClientBaseAmount = params.getUserInstrument().getActualClientOwnedBaseAmount().add(operationUserAmountBase);

                // e.g. 4000 or 1000
                newTotalBaseAmount = tempClientBaseAmount.setScale(-3, RoundingMode.HALF_UP);
            } else {
                // TODO: What should be the priority when closing positions?
                //  1) Max number of closed orders?
                //  2) Orders with the highest left to unhedge?
                //  3) Latest/Earliest orders?
                //  4) ????

                // HEDGE OUT
                // find all open orders with non-closed positions (left to unhedge > 0)
                // create 'consumption' records, i.e. which orders are used to unhedge and how much is used from each order.


                // this is the base amount of consumed orders. i.e. per order price / consumed quote amount
                // e.g. 1000 / 1.36 = 735.29 USD add to the operationUserAmountBase
                operationUserAmountBase = BigDecimal.ZERO;
                price = params.getTick().getBid();

                List<OrderConsumption> orderConsumptions = new ArrayList<>();
                List<Order> allForUnhendging = orderRepo.findAllForUnhendging(params.getUserId(), params.getInstrument().getLabel());
                Integer amountLeft = params.getUserAmount();
//                BigDecimal totalToUnhedge = allForUnhendging.stream()
//                        .map(Order::getQuoteAmountLeftToUnhedge)
//                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                for (int i = 0; amountLeft > 0; i++) {
                    Order orderToConsume = allForUnhendging.get(i);
                    BigDecimal quoteConsumptionAmount = orderToConsume.getQuoteAmountLeftToUnhedge().min(BigDecimal.valueOf(amountLeft));
                    amountLeft -= quoteConsumptionAmount.intValue();

                    operationUserAmountBase = operationUserAmountBase.add(quoteConsumptionAmount.divide(orderToConsume.getPrice(), 5, RoundingMode.HALF_DOWN));
                    orderConsumptions.add(new OrderConsumption(orderToConsume.getId(), quoteConsumptionAmount));
                }

                // should be >= 0
//                BigDecimal tempClientBaseAmount = params.getUserInstrument().getClientOwnedBaseAmount().subtract(operationUserAmountBase);
                tempClientBaseAmount = BigDecimal.ZERO.max(params.getUserInstrument().getActualClientOwnedBaseAmount().subtract(operationUserAmountBase));

                // e.g. 4000 or 1000
                newTotalBaseAmount = tempClientBaseAmount.setScale(-3, RoundingMode.HALF_UP);
            }

            BigDecimal executionBaseAmount = newTotalBaseAmount.subtract(currentTotal).abs();
            BigDecimal executionVolume = executionBaseAmount.divide(tradeContractSize, 5, RoundingMode.HALF_DOWN).abs();
            if (newTotalBaseAmount.compareTo(tempClientBaseAmount) >= 0) {
                // 3600 -> 4000 case
                newUserOwnedBase = tempClientBaseAmount;
                log.info("HEDGE IN: new total base amount: {}, current total: {}, operation user amount base: {}, total volume: {}", newTotalBaseAmount, currentTotal, operationUserAmountBase, executionVolume);
            } else {
                // 1400 -> 1000 case
                newUserOwnedBase = newTotalBaseAmount;
                log.info("HEDGE IN: new total base amount: {}, current total: {}, operation user amount base: {}, total volume: {}", newTotalBaseAmount, currentTotal, operationUserAmountBase, executionVolume);
            }

            return VolumeCalculation.builder()
                    .oldUserOwnedBase(params.getUserInstrument().getClientOwnedBaseAmount())
                    .oldHouseOwnedBase(params.getUserInstrument().getHouseOwnedBaseAmount())
                    .newUserOwnedBase(newUserOwnedBase)
                    .newHouseOwnedBase(newTotalBaseAmount.subtract(tempClientBaseAmount))
                    .originalAmount(params.getUserAmount())
//                            .userOwnedVolume(operationUserAmountBase.divide(totalVolume, 5, RoundingMode.HALF_DOWN))
                    .executionUserOwnedBase(operationUserAmountBase)
                    .executionVolume(executionVolume)
                    .executionBase(executionBaseAmount)
                    .price(BigDecimal.valueOf(price))
                    .build();
        }
    }
}
