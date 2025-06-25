package org.ipan.hedging.telegram.state;

public interface UserEdit {
    Long getSelectedUserId();

    UserEdit withSelectedUserId(Long value);
}
