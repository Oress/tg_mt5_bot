package org.ipan.hedging.telegram.statetransitions;

import java.lang.reflect.Method;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class StateHandlerCollector implements BeanPostProcessor {
    private final StateHandlerRegistry registry;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!bean.getClass().isAnnotationPresent(TransitionHandler.class)) {
            return bean;
        }

        for (Method method : bean.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(MatchStates.class)) {
                MatchState[] state = method.getAnnotation(MatchStates.class).value();
                for (MatchState matchState : state) {
                    registry.register(matchState, method, bean);
                }
            }

            if (method.isAnnotationPresent(MatchState.class)) {
                MatchState matchState = method.getAnnotation(MatchState.class);
                registry.register(matchState, method, bean);
            }
        }
        return bean;
    }
}