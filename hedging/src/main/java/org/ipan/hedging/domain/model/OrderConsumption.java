package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "hed_order_consumption")
@Slf4j
public class OrderConsumption {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_order_consumption")
    @SequenceGenerator(name = "seq_hed_order_consumption", sequenceName = "seq_hed_order_consumption", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "parent_order_id")
    private Order parentOrder;

    @ManyToOne
    @JoinColumn(name = "consumed_order_id")
    private Order consumedOrder;

    @Column(name = "consumed_quote_amount")
    private BigDecimal consumedQuoteAmount;

    @Column(name = "left_quote_amount")
    private BigDecimal leftQuoteAmount;

    @Column(name = "base_equivalent")
    private BigDecimal baseEquivalent;
}
