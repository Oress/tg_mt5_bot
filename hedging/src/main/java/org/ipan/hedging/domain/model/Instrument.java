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
@Table(name = "hed_instruments")
@Slf4j
public class Instrument {
    @Id
    private String label;

    @Column(name = "symbol_mini")
    private String symbolMini;

    @Column(name = "symbol_zero")
    private String symbolZero;

    @Column(name = "symbol_standard")
    private String symbolStandard;

    @Column(name = "sheet_id")
    private Integer sheetId;

    private String base;
    private String quote;

    @Enumerated(EnumType.STRING)
    private InstrumentMode mode;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public String getSymbolByMode() {
        return switch (mode) {
            case LONG -> quote;
            case SHORT -> base;
            default -> symbolStandard;
        };
    }
}
