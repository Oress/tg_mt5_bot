package org.ipan.hedging.domain.service;

import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.repository.UserRepo;
import org.ipan.hedging.domain.service.commands.users.CreateUserCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@AllArgsConstructor
@Slf4j
public class UserService {
    private final UserRepo userRepo;

    @Transactional
    public AppUser createUser(CreateUserCommand command) {
        AppUser appUser = userRepo.findById(command.getTelegramId()).orElse(null);
        if (appUser != null) {
            return appUser;
        }

        appUser = new AppUser();
        updateModelFromCommand(command, appUser);

        userRepo.save(appUser);

        return appUser;
    }

    @Transactional
    public AppUser updateUser(CreateUserCommand command) {
        AppUser appUser = userRepo.findById(command.getTelegramId()).orElse(null);

        updateModelFromCommand(command, appUser);

        return appUser;
    }

    @Transactional
    public AppUser setLanguage(Long userId, String language) {
        AppUser appUser = userRepo.findById(userId).orElse(null);

        if (!"en".equals(language) && !"ru".equals(language)) {
            throw new IllegalArgumentException("language is neither en nor ru");
        }

        appUser.setLanguageCode(language);

        return appUser;
    }

    @Transactional
    public void deactivateUser(Long selectedUserId) {
        AppUser appUser = userRepo.findById(selectedUserId).orElse(null);
        if (appUser != null) {
            appUser.setDisabled(true);
        }
    }

    private void updateModelFromCommand(CreateUserCommand command, AppUser appUser) {
        appUser.setTelegramId(command.getTelegramId());
        appUser.setTelegramUsername(command.getUsername());
        appUser.setTelegramFirstName(command.getFirstName());
        if (command.getLanguageCode() != null) {
            appUser.setLanguageCode(command.getLanguageCode());
        }
    }

    public AppUser getById(Long telegramId) {
        return userRepo.findById(telegramId).orElse(null);
    }
}
