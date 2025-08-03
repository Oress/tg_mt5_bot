package org.ipan.hedging.application.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;

@TestConfiguration
@Profile("test")
public class TestAsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
        // Return synchronous executor for tests
        return new SyncTaskExecutor();
    }
}