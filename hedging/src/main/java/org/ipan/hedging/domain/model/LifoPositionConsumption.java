package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "hed_lifo_position_consumptions")
@Slf4j
public class LifoPositionConsumption {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_lifo_position_consumptions")
    @SequenceGenerator(name = "seq_hed_lifo_position_consumptions", sequenceName = "seq_hed_lifo_position_consumptions", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "parent_order_id")
    private Order parentOrder;

    @ManyToOne
    @JoinColumn(name = "consumed_position_id")
    private LifoPosition consumedPosition;

    @Column(name = "consumed_quote_amount")
    private BigDecimal consumedQuoteAmount;

    @Column(name = "left_quote_amount")
    private BigDecimal leftQuoteAmount;

    @Column(name = "base_equivalent")
    private BigDecimal baseEquivalent;
}
