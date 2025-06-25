package org.ipan.hedging.domain.exception;

public class NotEnoughBalanceException extends EnergyRentException {
    public NotEnoughBalanceException(String message) {
        super(message);
    }

    public NotEnoughBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
