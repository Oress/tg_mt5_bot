package org.ipan.hedging.domain.exception;

public class WalletAlreadyHasActiveSessionException extends EnergyRentException {
    public WalletAlreadyHasActiveSessionException(String message) {
        super(message);
    }

    public WalletAlreadyHasActiveSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
