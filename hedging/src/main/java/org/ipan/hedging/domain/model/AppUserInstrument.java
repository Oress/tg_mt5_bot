package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;
import org.ipan.hedging.domain.service.VolumeCalculation;

import java.math.BigDecimal;
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

    private Boolean active = Boolean.FALSE;

    @Column(name = "last_ggl_sheet_sync")
    private Instant lastGoogleSheetSync;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    // e.g. EUR/USD; EUR - base, USD - quote
    // Data below is used for tracking hedging quote assets.
//    @Column(name = "total_owned_base_amount")
//    private BigDecimal totalOwnedBaseAmount = BigDecimal.ZERO;

    @Column(name = "client_owned_base_amount")
    private BigDecimal clientOwnedBaseAmount = BigDecimal.ZERO;

    @Column(name = "house_owned_base_amount")
    private BigDecimal houseOwnedBaseAmount = BigDecimal.ZERO;

    @Column(name = "client_owned_quote_amount")
    private BigDecimal clientOwnedQuoteAmount = BigDecimal.ZERO;


    // e.g. houseOwnedBaseAmount is -465 USD, clientOwnedBaseAmount is 1000 USD
    // this means that House owes the client 465 USD
    public BigDecimal getActualClientOwnedBaseAmount() {
        return houseOwnedBaseAmount.compareTo(BigDecimal.ZERO) < 0
                ? clientOwnedBaseAmount.subtract(houseOwnedBaseAmount)
                : clientOwnedBaseAmount;
    }

    public BigDecimal getTotalOwnedBaseAmount() {
        // total = client + max(house,0)
        //  houseOwnedBaseAmount can be negative and this means that(e.g. 1465 USD and -465 USD):
        // 1) house owns the client some amount of money
        return clientOwnedBaseAmount.add(houseOwnedBaseAmount);
    }

    public void addVolumeShortMode(VolumeCalculation volumeCalculation) {
        if (volumeCalculation != null) {
            this.clientOwnedBaseAmount = volumeCalculation.getNewUserOwnedBase();
            this.houseOwnedBaseAmount = volumeCalculation.getNewHouseOwnedBase();
        } else {
            log.warn("Attempt to add null total base amount for user instrument: {}", this);
        }
    }

    public void subtractUserAndHousePartsLongMode(VolumeCalculation volumeCalculation) {
        this.clientOwnedBaseAmount = volumeCalculation.getNewUserOwnedBase();
        this.houseOwnedBaseAmount = volumeCalculation.getNewHouseOwnedBase();
        this.clientOwnedQuoteAmount = this.clientOwnedQuoteAmount.subtract(BigDecimal.valueOf(volumeCalculation.getOriginalAmount()));
    }



    public void subtractUserAndHousePartsShortMode(VolumeCalculation volumeCalculation) {
        this.clientOwnedBaseAmount = volumeCalculation.getNewUserOwnedBase();
        this.houseOwnedBaseAmount = volumeCalculation.getNewHouseOwnedBase();
    }


    public void addUserAndHousePartsLongMode(VolumeCalculation volumeCalculation) {
        this.clientOwnedBaseAmount = volumeCalculation.getNewUserOwnedBase();
        this.houseOwnedBaseAmount = volumeCalculation.getNewHouseOwnedBase();
        this.clientOwnedQuoteAmount = this.clientOwnedQuoteAmount.add(BigDecimal.valueOf(volumeCalculation.getOriginalAmount()));
    }

/*
    public void houseToClientTransferOrder(VolumeCalculation volumeCalculation) {
        // TODO: check that houseOwnedBaseAmount is greater than or equal to clientOwnedBaseAmount
        this.clientOwnedQuoteAmount = this.clientOwnedQuoteAmount.add(BigDecimal.valueOf(volumeCalculation.getOriginalAmount()));
        this.clientOwnedBaseAmount = this.clientOwnedBaseAmount
                .subtract(volumeCalculation.getUserOwnedBase());
    }
*/
}
