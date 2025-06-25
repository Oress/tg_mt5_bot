package org.ipan.hedging.telegram.statetransitions;

import org.ipan.hedging.telegram.States;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface MatchState {
    States state() default States.START;

    int updateTypes() default UpdateType.NONE;

    boolean forAdmin() default false;

    String callbackData() default "";
}
