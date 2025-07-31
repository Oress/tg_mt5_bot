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
@Table(name = "hed_user_balance_actions")
@Slf4j
public class UserBalanceAction {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_user_balance_actions")
    @SequenceGenerator(name = "seq_hed_user_balance_actions", sequenceName = "seq_hed_user_balance_actions", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_instrument_id")
    private AppUserInstrument userInstrument;

    @Enumerated(EnumType.STRING)
    private BalanceActionType type;

    private BigDecimal amount;

    private Instant date;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public BigDecimal getAmountForGoogleSheet() {
        if (type == BalanceActionType.DEPOSIT) {
            return amount;
        } else if (type == BalanceActionType.WITHDRAWAL) {
            return amount.negate();
        }
        return BigDecimal.ZERO;
    }
}
