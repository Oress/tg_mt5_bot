package org.ipan.hedging.domain.exception;

public class NotManagerException extends EnergyRentException {
    public NotManagerException(String message) {
        super(message);
    }

    public NotManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
