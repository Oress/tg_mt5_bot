package org.ipan.hedging.domain.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.HouseOwnedPosition;
import org.ipan.hedging.domain.model.HouseOwnedPositionType;
import org.ipan.hedging.domain.model.HousePositionConsumption;
import org.ipan.hedging.domain.model.Order;
import org.ipan.hedging.domain.model.repository.OrderRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class ServicePnlCalculator {

    private OrderRepo orderRepository;

    @Transactional
    public void recalculateServicePnlForInstrument(Long userInstrumentId) {
        List<Order> orders = orderRepository.findAllByUserInstrumentIdOrderByCreatedAtDesc(userInstrumentId);

        for (Order order : orders) {
            List<HousePositionConsumption> housePositionConsumptions = order.getHousePositionConsumptions();

            for (HousePositionConsumption hpc : housePositionConsumptions) {
                HouseOwnedPosition consumedPosition = hpc.getConsumedPosition();
                BigDecimal newPnl = calculatePnl(consumedPosition.getType(), hpc.getConsumedAmount(), consumedPosition.getOrder().getPrice(), order.getPrice());
                log.info("Recalculated PnL for house position consumption {}: old PnL = {}, new PnL = {}", hpc.getId(), hpc.getPnl(), newPnl);
                hpc.setPnl(newPnl);
            }
        }
    }

    public BigDecimal calculatePnl(HouseOwnedPositionType type, BigDecimal consumptionAmount, BigDecimal oldPrice, BigDecimal newPrice) {
        BigDecimal pnl = null;

        if (HouseOwnedPositionType.LONG.equals(type)) {
            // PnL = position_usd * ((exit_rate / entry_rate) - 1)
            pnl = consumptionAmount.multiply(
                    newPrice.divide(oldPrice, 8, RoundingMode.HALF_DOWN)
                            .subtract(BigDecimal.ONE)
            );
        } else if (HouseOwnedPositionType.SHORT.equals(type)) {
            // PnL = -1 * position_eur * (exit_rate - entry_rate)
            pnl = consumptionAmount.multiply(newPrice.subtract(oldPrice)).negate();
        } else {
            log.error("Unsupported house position type: {}", type);
        }

        return pnl;
    }
}
