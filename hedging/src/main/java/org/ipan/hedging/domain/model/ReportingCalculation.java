package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "hed_reporting_calculations")
@Getter
@Setter
public class ReportingCalculation {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_reporting_calculations")
    @SequenceGenerator(name = "seq_hed_reporting_calculations", sequenceName = "seq_hed_reporting_calculations", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne
    @JoinColumn(name = "user_balance_action_id")
    private UserBalanceAction userBalanceAction;

    @ManyToOne
    @JoinColumn(name = "user_instrument_id")
    private AppUserInstrument userInstrument;

    private Instant date;

    private BigDecimal amount;

    private BigDecimal fix;

    private BigDecimal usdt;

    @Column(name = "commission")
    private BigDecimal commission;

    @Column(name = "in_position")
    private BigDecimal inPosition;

    private BigDecimal rate;

    private BigDecimal deposit;

    @Column(name = "deposit_total")
    private BigDecimal depositTotal;

    @Column(name = "deposit_payment")
    private BigDecimal depositPayment;

    private BigDecimal balance;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public BigDecimal getDepositPayment() {
        return depositPayment == null ? BigDecimal.ZERO : depositPayment;
    }
}