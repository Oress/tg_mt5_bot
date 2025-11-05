package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.exception.NegativeBalanceException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AppUserInstrument> availableInstruments = new HashSet<>();

    @Column(name = "terminal_to_use")
    private String terminalToUse = AppConstants.TERMINAL_PROXY1;

    @Column(name = "balance_usd")
    private BigDecimal balanceUsd = BigDecimal.ZERO;

    @Column(name = "dashboard_pwd")
    private String dashboardPwd;

    @Column(name = "username")
    private String telegramUsername;

    @Column(name = "first_name")
    private String telegramFirstName;

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

    public BigDecimal getBalanceUsd() {
        return balanceUsd == null ? BigDecimal.ZERO : balanceUsd;
    }

    public List<Instrument> getInstruments() {
        return availableInstruments.stream()
                .map(AppUserInstrument::getInstrument)
                .toList();
    }

    public boolean hasAccount() {
        return mt5AccNumber != null && mt5AccPwdEnc != null;
    }

    public boolean hasInstruments() {
        return !availableInstruments.isEmpty();
    }

    public boolean containsInstument(String instrumentLabel) {
        return availableInstruments.stream().anyMatch(i -> i.getInstrument().getLabel().equals(instrumentLabel));
    }

    public boolean containsInstrumentByLabel(String label) {
        for (Instrument instrument : availableInstruments.stream().map(AppUserInstrument::getInstrument).toList()) {
            if (instrument.getLabel().equals(label)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsInstumentBySymbol(String symbol) {
        for (Instrument instrument : availableInstruments.stream().map(AppUserInstrument::getInstrument).toList()) {
            if (switch (accountType) {
                case STANDARD -> symbol.equals(instrument.getSymbolStandard());
                case MINI -> symbol.equals(instrument.getSymbolMini());
                case ZERO -> symbol.equals(instrument.getSymbolZero());
                default -> symbol.equals(instrument.getSymbolMini());
            }) {
                return true;
            }
        }
        return false;
    }


    public String getSymbolByLabelAndAccType(String label) {
        for (Instrument instrument : availableInstruments.stream().map(AppUserInstrument::getInstrument).toList()) {
            if (instrument.getLabel().equals(label)) {
                return switch (accountType) {
                    case STANDARD -> instrument.getSymbolStandard();
                    case MINI -> instrument.getSymbolMini();
                    case ZERO -> instrument.getSymbolZero();
                    default -> instrument.getSymbolStandard();
                };
            }
        }
        return null;
    }

    public AppUserInstrument findUserInstrumentBySymbol(String symbol) {
        for (AppUserInstrument userInstrument : availableInstruments) {
            Instrument instrument = userInstrument.getInstrument();
            if (instrument.getSymbolZero().equals(symbol)
                    || instrument.getSymbolMini().equals(symbol)
                    || instrument.getSymbolStandard().equals(symbol)) {
                return userInstrument;
            }
        }
        return null;
    }

    public Instrument findInstrumentBySymbol(String symbol) {
        for (Instrument instrument : availableInstruments.stream().map(AppUserInstrument::getInstrument).toList()) {
            if (instrument.getSymbolZero().equals(symbol)
                    || instrument.getSymbolMini().equals(symbol)
                    || instrument.getSymbolStandard().equals(symbol)) {
                return instrument;
            }
        }
        return null;
    }

    public AppUserInstrument addInstrument(Instrument instrument) {
        AppUserInstrument userInstrument = new AppUserInstrument();
        userInstrument.setInstrument(instrument);
        userInstrument.setUser(this);
        userInstrument.setCommissionCoef(AppConstants.DEFAULT_COMMISSION_PERCENTAGE);
        userInstrument.setSheetId(instrument.getSheetId());
        userInstrument.setActive(true);
        availableInstruments.add(userInstrument);
        return userInstrument;
    }

    public AppUserInstrument findUserInstrument(String instrumentLabel) {
        return availableInstruments.stream()
                .filter(ui -> ui.getInstrument().getLabel().equals(instrumentLabel))
                .findFirst()
                .orElse(null);
    }

    public void addBalanceUsd(BigDecimal amount) {
        if (balanceUsd == null) {
            balanceUsd = BigDecimal.ZERO;
        }
        balanceUsd = balanceUsd.add(amount);
    }

    public void subtractBalanceUsd(BigDecimal amount) {
        if (balanceUsd == null) {
            balanceUsd = BigDecimal.ZERO;
        }
        balanceUsd = balanceUsd.subtract(amount);
        if (balanceUsd.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("User {} balance is negative: {}", telegramId, balanceUsd);
            throw new NegativeBalanceException();
        }
    }

    public boolean hasAccountInfo() {
        return mt5AccNumber != null && mt5AccPwdEnc != null;
    }
}
