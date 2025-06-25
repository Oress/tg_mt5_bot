package org.ipan.hedging.domain.exception;

public class EnergyRentException extends RuntimeException {
    public EnergyRentException(String message) {
        super(message);
    }

    public EnergyRentException(String message, Throwable cause) {
        super(message, cause);
    }

}
