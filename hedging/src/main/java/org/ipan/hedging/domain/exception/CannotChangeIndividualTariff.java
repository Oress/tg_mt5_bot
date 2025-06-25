package org.ipan.hedging.domain.exception;

public class CannotChangeIndividualTariff extends EnergyRentException {
    public CannotChangeIndividualTariff(String message) {
        super(message);
    }

    public CannotChangeIndividualTariff(String message, Throwable cause) {
        super(message, cause);
    }
}
