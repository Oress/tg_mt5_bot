package org.ipan.hedging.domain.exception;

public class WalletSessionAlreadyDeactivatedException extends EnergyRentException {
    public WalletSessionAlreadyDeactivatedException(String message) {
        super(message);
    }

    public WalletSessionAlreadyDeactivatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
