package org.ipan.hedging.domain.model.projections;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class EnabledInstrumentPerUser {
    private String label;
    private Boolean isEnabled;
}
