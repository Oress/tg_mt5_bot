package org.ipan.hedging.telegram.mapdb;

import org.ipan.hedging.telegram.States;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserEdit;
import org.ipan.hedging.telegram.state.UserSearchState;
import org.ipan.hedging.telegram.state.UserState;
import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TelegramStateInMem implements TelegramState {
    private final HTreeMap<Long, UserStateInMem> userStateMap;
    private final HTreeMap<Long, UserEditInMem> userEditMap;
    private final HTreeMap<Long, UserSearchStateInMem> userSearchState;

    public TelegramStateInMem(DB db, ObjectMapper objectMapper) {
        this.userStateMap = db.hashMap("userState")
                .keySerializer(Serializer.LONG)
                .valueSerializer(new GenericSerializer<>(UserStateInMem.class, objectMapper))
                .createOrOpen();

        this.userEditMap = db.hashMap("userEdit")
                .keySerializer(Serializer.LONG)
                .valueSerializer(new GenericSerializer<>(UserEditInMem.class, objectMapper))
                .createOrOpen();

        this.userSearchState = db.hashMap("userSearchState")
                .keySerializer(Serializer.LONG)
                .valueSerializer(new GenericSerializer<>(UserSearchStateInMem.class, objectMapper))
                .createOrOpen();
    }

    @Override
    public UserState getOrCreateUserState(Long userId) {
        return this.userStateMap.computeIfAbsent(userId, key -> UserStateInMem.builder().telegramId(userId).state(States.START).build());
    }

    @Override
    public UserState updateUserState(Long userId, UserState userState) {
        return this.userStateMap.put(userId, UserStateInMem.of(userState));
    }

    @Override
    public UserEdit getOrCreateUserEdit(Long userId) {
        return this.userEditMap.computeIfAbsent(userId, key -> UserEditInMem.builder().build());
    }

    @Override
    public UserEdit updateUserEdit(Long userId, UserEdit userEdit) {
        return this.userEditMap.put(userId, UserEditInMem.of(userEdit));
    }

    @Override
    public UserEdit removeUserEdit(Long userId) {
        return this.userEditMap.remove(userId);
    }

    @Override
    public UserSearchState getOrCreateUserSearchState(Long userId) {
        return this.userSearchState.computeIfAbsent(userId, key -> UserSearchStateInMem.builder().build());
    }

    @Override
    public UserSearchState updateUserSearchState(Long userId, UserSearchState userSearchState) {
        return this.userSearchState.put(userId, UserSearchStateInMem.of(userSearchState));
    }

    @Override
    public UserSearchState removeUserSearchState(Long userId) {
        return this.userSearchState.remove(userId);
    }
}
