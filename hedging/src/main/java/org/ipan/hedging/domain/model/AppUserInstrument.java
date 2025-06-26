package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "hed_user_instruments")
@Slf4j
public class AppUserInstrument {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_user_instruments")
    @SequenceGenerator(name = "seq_hed_user_instruments", sequenceName = "seq_hed_user_instruments", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "instrument_label")
    private Instrument instrument;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "sheet_id")
    private Integer sheetId;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
