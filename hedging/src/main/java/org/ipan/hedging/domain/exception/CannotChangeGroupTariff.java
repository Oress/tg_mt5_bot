package org.ipan.hedging.domain.exception;

public class CannotChangeGroupTariff extends EnergyRentException {
    public CannotChangeGroupTariff(String message) {
        super(message);
    }

    public CannotChangeGroupTariff(String message, Throwable cause) {
        super(message, cause);
    }
}
