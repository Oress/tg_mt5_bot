package org.ipan.hedging.domain.exception;

public class UserIsManagerException extends EnergyRentException {
    public UserIsManagerException(String message) {
        super(message);
    }

    public UserIsManagerException(String message, Throwable cause) {
        super(message, cause);
    }

}
