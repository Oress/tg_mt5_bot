package org.ipan.hedging.telegram.statetransitions;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;

import org.ipan.hedging.telegram.state.UserState;
import org.telegram.telegrambots.meta.api.objects.Update;

import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
public class TransitionMatcher {
    private final BiPredicate<UserState, Update> test;
    @Getter
    private final Object bean;
    @Getter
    private final Method method;

    public boolean matches(UserState userState, Update update) {
        return test.test(userState, update);
    }
}