package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// e.g. EUR/USD; EUR - base, USD - quote
@Entity
@Getter
@Setter
@Table(name = "hed_orders")
@Slf4j
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_orders")
    @SequenceGenerator(name = "seq_hed_orders", sequenceName = "seq_hed_orders", allocationSize = 1)
    private Long id;

    private UUID correlationId;

    @ManyToOne
    @JoinColumn(name = "user_instrument_id")
    private AppUserInstrument userInstrument;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private LifoPosition lifoPosition;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private HouseOwnedPosition houseOwnedPosition;

    @OneToMany(mappedBy = "parentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LifoPositionConsumption> consumptions = new ArrayList<>();

    @OneToMany(mappedBy = "parentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HousePositionConsumption> housePositionConsumptions = new ArrayList<>();

    private Long deal;

    @Column(name = "mt5_order")
    private Long order;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "acc_number")
    private Integer mt5AccNumber;

    @Column(name = "mt5_resp_code")
    private Integer mt5RespCode;

    @Column(name = "mt5_resp_comment")
    private Integer mt5RespComment;

    // base equivalent of amount entered by user
    // in case of closing USD/CAD positions it is a oldOrder.price * closeQuoteAmount
    @Column(name = "user_base_amount")
    private BigDecimal userBaseAmount;

    @Column(name = "user_entered_quote_amount")
    private BigDecimal userEnteredQuoteAmount;

    // executed_ fields are only for actual MT5 orders
    @Column(name = "executed_volume")
    private BigDecimal executedVolume;

    @Column(name = "executed_base_amount")
    private BigDecimal executedBaseAmount;

    @Column(name = "house_base_amount_from")
    private BigDecimal houseBaseAmountFrom = BigDecimal.ZERO;

    @Column(name = "house_base_amount_to")
    private BigDecimal houseBaseAmountTo = BigDecimal.ZERO;

    @Column(name = "client_base_amount_from")
    private BigDecimal clientBaseAmountFrom = BigDecimal.ZERO;
//
    @Column(name = "client_base_amount_to")
    private BigDecimal clientBaseAmountTo = BigDecimal.ZERO;

    private BigDecimal price;
    private BigDecimal bid;
    private BigDecimal ask;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    // TODO: 0 should not be an option here
    public HouseOwnedPosition createHouseOwnedPosition(BigDecimal actualHouseAmount) {
        HouseOwnedPosition houseOwnedPosition = new HouseOwnedPosition();
        houseOwnedPosition.setOrder(this);

        // TODO: does it depend on the mode?
        if (actualHouseAmount.compareTo(BigDecimal.ZERO) < 0) {
            houseOwnedPosition.setType(HouseOwnedPositionType.SHORT);
            houseOwnedPosition.setRemainingAmount(actualHouseAmount.negate());
        } else {
            houseOwnedPosition.setType(HouseOwnedPositionType.LONG);
            houseOwnedPosition.setRemainingAmount(actualHouseAmount);
        }

        this.setHouseOwnedPosition(houseOwnedPosition);

        return houseOwnedPosition;
    }


    public LifoPosition createLifoPosition(Integer userAmount) {
        LifoPosition lifoPosition = new LifoPosition();
        lifoPosition.setOrder(this);
        lifoPosition.setRemainingQuoteAmount(BigDecimal.valueOf(userAmount));

        this.setLifoPosition(lifoPosition);

        return lifoPosition;
    }

    public BigDecimal getUserAmountForGoogleSheet() {
        BigDecimal result = getUserEnteredAmount();
        if (isHedge()) {
            result = result.negate();
        }
        return result;
    }

    public BigDecimal getBaseEquivalentForGoogleSheet() {
        BigDecimal result = userBaseAmount;
        if (isHedge()) {
            result = result.negate();
        }
        return result;
    }


    private boolean isHedge() {
        return ((OrderType.MT5_LONG.equals(type) || OrderType.HOUSE_CLIENT_TRANSFER_LONG.equals(type)) && InstrumentMode.LONG.equals(userInstrument.getInstrument().getMode()))
                || ((OrderType.MT5_SHORT.equals(type) || OrderType.HOUSE_CLIENT_TRANSFER_SHORT.equals(type)) && InstrumentMode.SHORT.equals(userInstrument.getInstrument().getMode()));
    }

    public boolean isHedgeOut() {
        return ((OrderType.MT5_LONG.equals(type) || OrderType.HOUSE_CLIENT_TRANSFER_LONG.equals(type)) && InstrumentMode.SHORT.equals(userInstrument.getInstrument().getMode()))
                || ((OrderType.MT5_SHORT.equals(type) || OrderType.HOUSE_CLIENT_TRANSFER_SHORT.equals(type)) && InstrumentMode.LONG.equals(userInstrument.getInstrument().getMode()));
    }

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

/*
    public void decreaseQuoteAmountLeftToUnhedge(BigDecimal quoteConsumptionAmount) {
        if (quoteAmountLeftToUnhedge != null) {
            quoteAmountLeftToUnhedge = quoteAmountLeftToUnhedge.subtract(quoteConsumptionAmount);
            if (quoteAmountLeftToUnhedge.compareTo(BigDecimal.ZERO) < 0) {
                log.error("Quote amount left to unhedge for order {} is negative: {}", this, quoteAmountLeftToUnhedge);
                throw new IllegalStateException("Quote amount left to unhedge for order is negative: " + this);
            }
        } else {
            log.warn("Attempt to decrease null quote amount left to unhedge for order: {}", this);
        }
    }
*/
}
