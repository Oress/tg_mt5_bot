package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Table(name = "hed_users")
@Slf4j
public class AppUser {
    @Id
    @Column(name = "tg_id")
    private Long telegramId;

    @ManyToMany
    @JoinTable(
            name = "hed_user_instruments",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "instrument_label")
    )
    private Set<Instrument> availableInstruments = new HashSet<>();

    @Column(name = "username")
    private String telegramUsername;

    @Column(name = "first_name")
    private String telegramFirstName;

    @Column(name = "sheet_id")
    private Integer sheetId;

    @Column(name = "ggl_sheet_id")
    private String googleSheetId;

    @Column(name = "account_type")
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(name = "mt5_acc_number")
    private Integer mt5AccNumber;

    @Column(name = "mt5_acc_pwd_enc")
    private byte[] mt5AccPwdEnc;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;

    private Boolean disabled = Boolean.FALSE;

    @Column(name = "language_code")
    private String languageCode;

    @Column(name = "disabled_reason")
    private String disabledReason;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public boolean hasAccount() {
        return mt5AccNumber != null && mt5AccPwdEnc != null;
    }

    public boolean hasInstruments() {
        return !availableInstruments.isEmpty();
    }

    public boolean containsInstument(String instrumentLabel) {
        return availableInstruments.stream().anyMatch(i -> i.getLabel().equals(instrumentLabel));
    }

    public String getSymbolByLabelAndAccType(String symbol) {
        for (Instrument instrument : availableInstruments) {
            if (instrument.getLabel().equals(symbol)) {
                return switch (accountType) {
                    case STANDARD -> instrument.getSymbolStandard();
                    case MINI -> instrument.getSymbolMini();
                    case ZERO -> instrument.getSymbolZero();
                    default -> instrument.getSymbolMini();
                };
            }
        }
        return null;
    }

    public Instrument findInstrumentBySymbol(String symbol) {
        for (Instrument instrument : availableInstruments) {
            if (instrument.getSymbolZero().equals(symbol)
                    || instrument.getSymbolMini().equals(symbol)
                    || instrument.getSymbolStandard().equals(symbol)) {
                return instrument;
            }
        }
        return null;
    }

}
