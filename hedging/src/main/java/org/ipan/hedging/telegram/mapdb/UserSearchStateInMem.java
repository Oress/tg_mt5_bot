package org.ipan.hedging.telegram.mapdb;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.ipan.hedging.telegram.state.UserSearchState;

@Value
@With
@Builder
@Jacksonized
public class UserSearchStateInMem implements UserSearchState {
    Integer currentPage;
    String query;

    public static UserSearchStateInMem of(UserSearchState prototype) {
        return UserSearchStateInMem.builder()
                .currentPage(prototype.getCurrentPage())
                .query(prototype.getQuery())
                .build();
    }
}
