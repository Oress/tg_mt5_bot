package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "hed_house_owned_positions")
@Slf4j
@Getter
@Setter
public class HouseOwnedPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_house_owned_positions")
    @SequenceGenerator(name = "seq_hed_house_owned_positions", sequenceName = "seq_hed_house_owned_positions", allocationSize = 1)
    private Long id;

    // I can take the following fields from Order:
    // userInstrument, price, userBaseAmount, userEnteredQuoteAmount
    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    private HouseOwnedPositionType type;

    @Column(name = "remaining_amount")
    private BigDecimal remainingAmount;

    @Column(name = "closed_at")
    private Instant closedAt;

    public void decreaseRemainingAmount(BigDecimal consumptionAmount) {
        if (remainingAmount != null) {
            remainingAmount = remainingAmount.subtract(consumptionAmount);
            if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
                log.error("Amount left for house position {} is negative: {}", this, remainingAmount);
                throw new IllegalStateException("Amount left for house position is negative: " + this);
            }
        } else {
            log.warn("Attempt to decrease null quote amount left to house position: {}", this);
        }
    }

    public boolean isConsumed() {
        if (remainingAmount == null) {
            log.warn("Checking consumption status for order {} with null remaining quote amount", this);
            return false;
        }
        boolean consumed = remainingAmount.compareTo(BigDecimal.ZERO) == 0;
        if (consumed && closedAt == null) {
            closedAt = Instant.now();
        }
        return consumed;
    }
}
