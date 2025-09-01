package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;


@Entity
@Getter
@Setter
@Table(name = "hed_user_commission_withdrawals")
@Slf4j
public class CommissionWithdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_user_commission_withdrawals")
    @SequenceGenerator(name = "seq_hed_user_commission_withdrawals", sequenceName = "seq_hed_user_commission_withdrawals", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_instrument_id")
    private AppUserInstrument userInstrument;

    private BigDecimal amount;

    private Instant date;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
