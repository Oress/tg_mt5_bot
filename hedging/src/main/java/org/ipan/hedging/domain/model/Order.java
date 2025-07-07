package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// e.g. EUR/USD; EUR - base, USD - quote
@Entity
@Getter
@Setter
@Table(name = "hed_orders")
@Slf4j
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_instrument_id")
    private AppUserInstrument userInstrument;

    private Long deal;

    @Column(name = "mt5_order")
    private Long order;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "acc_number")
    private Integer mt5AccNumber;

    @Column(name = "user_base_amount")
    private BigDecimal userBaseAmount;

    @Column(name = "user_entered_quote_amount")
    private BigDecimal userEnteredQuoteAmount;

    // executed_ fields are only for actual MT5 orders
    @Column(name = "executed_volume")
    private BigDecimal executedVolume;

    @Column(name = "executed_base_amount")
    private BigDecimal executedBaseAmount;

    @Column(name = "quote_amount_left_to_unhedge")
    private BigDecimal quoteAmountLeftToUnhedge;

    @Column(name = "house_base_amount_from")
    private BigDecimal houseBaseAmountFrom;

    @Column(name = "house_base_amount_to")
    private BigDecimal houseBaseAmountTo;

    @Column(name = "client_base_amount_from")
    private BigDecimal clientBaseAmountFrom;

    @Column(name = "client_base_amount_to")
    private BigDecimal clientBaseAmountTo;

    private BigDecimal price;
    private BigDecimal bid;
    private BigDecimal ask;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public BigDecimal getUserEnteredAmount() {
        if (userInstrument != null && userInstrument.getInstrument() != null) {
            return userInstrument.getInstrument().getMode() == InstrumentMode.SHORT
                    ? executedBaseAmount
                    : userEnteredQuoteAmount;
        } else {
            log.warn("User instrument or instrument is null for order: {}", this);
            return BigDecimal.ZERO;
        }

    }
}
