package org.ipan.hedging.application.service;

import lombok.AllArgsConstructor;
import org.ipan.hedging.domain.AppConstants;
import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.AppUserInstrument;
import org.ipan.hedging.domain.model.Instrument;
import org.ipan.hedging.domain.model.ReportingCalculation;
import org.ipan.hedging.domain.model.repository.ReportingCalculationRepository;
import org.ipan.hedging.domain.model.repository.UserBalanceActionRepo;
import org.ipan.hedging.domain.model.repository.UserBalanceActionsRepository;
import org.ipan.hedging.telegram.TelegramMessages;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
@AllArgsConstructor
public class MainMenuStatsService {
    private final ReportingCalculationRepository reportingCalculationRepository;
    private final UserBalanceActionsRepository userBalanceActionsRepository;
    private final UserBalanceActionRepo userBalanceActionRepo;

    @Transactional(readOnly = true)
    public TelegramMessages.MainMenuPayload createPayload(AppUser user) {
        TelegramMessages.MainMenuPayload mainMenuPayload = new TelegramMessages.MainMenuPayload();

        Set<AppUserInstrument> availableInstruments = user.getAvailableInstruments();
        for (AppUserInstrument userInstrument : availableInstruments) {
            Instrument instrument = userInstrument.getInstrument();
            ReportingCalculation lastRow = reportingCalculationRepository.getLastRowForTheUserInstrument(userInstrument.getId(), Limit.of(1));

            if (lastRow != null) {
                if (AppConstants.USD_CAD.equals(instrument.getLabel())) {
                    mainMenuPayload.setCadBalance(lastRow.getBalance());
                } else if (AppConstants.EUR_USD.equals(instrument.getLabel())) {
                    mainMenuPayload.setEurBalance(lastRow.getBalance());
                }
            }
        }

        BigDecimal sumOfAllActionsByUser = userBalanceActionRepo.getSumOfAllActionsByUser(user.getTelegramId());
        mainMenuPayload.setDeposits(sumOfAllActionsByUser != null ? sumOfAllActionsByUser : BigDecimal.ZERO);
        return mainMenuPayload;
    }
}
