package org.ipan.hedging.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.Config;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.model.AppUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class TerminalBroker {
    private final AtomicInteger nextTerminalIndex = new AtomicInteger(0);
    private final Map<String, Mt5Service> mt5ServiceMap;

    private final List<String> terminalNames = List.of(
            AppConstants.TERMINAL_PROXY1,
            AppConstants.TERMINAL_PROXY2,
            AppConstants.TERMINAL_PROXY3,
            AppConstants.TERMINAL_PROXY4,
            AppConstants.TERMINAL_PROXY5,
            AppConstants.TERMINAL_PROXY6,
            AppConstants.TERMINAL_PROXY7,
            AppConstants.TERMINAL_PROXY8,
            AppConstants.TERMINAL_PROXY9,
            AppConstants.TERMINAL_PROXY10
    );

    public String getNextTerminalToUse() {
        return terminalNames.get(nextTerminalIndex.getAndIncrement() % terminalNames.size());
    }

    public Mt5Service getMt5Service(AppUser user) {
        String brokerName = user.getTerminalToUse();
        Mt5Service service = mt5ServiceMap.get(brokerName);
        if (service == null) {
            log.warn("Could not find Mt5Service for broker name {}, falling back to main terminal", brokerName);
            service = getMainTerminal();
        }
        return service;
    }

    public Mt5Service getMainTerminal() {
        return mt5ServiceMap.get(Config.MAIN_MT5);
    }
}
