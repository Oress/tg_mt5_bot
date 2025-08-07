package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Slf4j
@Table(name = "hed_lifo_positions")
public class LifoPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_lifo_positions")
    @SequenceGenerator(name = "seq_hed_lifo_positions", sequenceName = "seq_hed_lifo_positions", allocationSize = 1)
    private Long id;

    // I can take the following fields from Order:
    // userInstrument, price, userBaseAmount, userEnteredQuoteAmount
    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "remaining_amount")
    private BigDecimal remainingAmount;

    @Column(name = "closed_at")
    private Instant closedAt;

    public void decreaseRemainingQuoteAmount(BigDecimal quoteConsumptionAmount) {
        if (remainingAmount != null) {
            remainingAmount = remainingAmount.subtract(quoteConsumptionAmount);
            if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
                log.error("Quote amount left to unhedge for order {} is negative: {}", this, remainingAmount);
                throw new IllegalStateException("Quote amount left to unhedge for order is negative: " + this);
            }
        } else {
            log.warn("Attempt to decrease null quote amount left to unhedge for order: {}", this);
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
