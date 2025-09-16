package org.ipan.hedging.domain.exception;

public class CoverageException extends EnergyRentException {
    public CoverageException(String message) {
        super(message);
    }

    public CoverageException(String message, Throwable cause) {
        super(message, cause);
    }
}
