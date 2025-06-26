package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
    @JoinColumn(name = "user_id")
    private AppUser user;

    private Long deal;

    @Column(name = "mt5_order")
    private Long order;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    private String instrument;

    @Column(name = "acc_number")
    private Integer mt5AccNumber;

    @Column(name = "euro_amount")
    private BigDecimal euroAmount;

    private BigDecimal volume;
    private BigDecimal price;
    private BigDecimal bid;
    private BigDecimal ask;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
