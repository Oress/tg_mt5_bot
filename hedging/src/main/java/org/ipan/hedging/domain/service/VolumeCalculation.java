package org.ipan.hedging.domain.service;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Value
public class VolumeCalculation {
    Integer originalAmount;
    BigDecimal price;

//    BigDecimal houseOwnedVolume = BigDecimal.ZERO;
//    BigDecimal houseOwnedBase = BigDecimal.ZERO;

    BigDecimal oldUserOwnedBase;
    BigDecimal newUserOwnedBase;

    BigDecimal oldHouseOwnedBase;
    BigDecimal newHouseOwnedBase;

    BigDecimal executionVolume;
    BigDecimal executionBase;

    BigDecimal executionUserOwnedBase;
    BigDecimal executionHouseOwnedBase;

    List<OrderConsumption> ordersForConsumption;

    public static class VolumeCalculationBuilder {
        public VolumeCalculation build() {
            validateNonNegative(executionVolume, "executionUserOwnedBase");
            validateNonNegative(executionBase, "executionUserOwnedBase");
            return new VolumeCalculation(originalAmount, price, oldUserOwnedBase, newUserOwnedBase,
                    oldHouseOwnedBase, newHouseOwnedBase, executionVolume, executionBase,
                    executionUserOwnedBase, executionHouseOwnedBase, ordersForConsumption);
        }

        private void validateNonNegative(BigDecimal value, String fieldName) {
            if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative: " + value);
            }
        }
    }

}
