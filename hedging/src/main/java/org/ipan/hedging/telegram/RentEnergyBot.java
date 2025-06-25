package org.ipan.hedging.telegram;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;


import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.UserRole;
import org.ipan.hedging.domain.service.UserService;
import org.ipan.hedging.domain.service.commands.users.CreateUserCommand;
import org.ipan.hedging.telegram.i18n.TgUserLocaleHolder;
import org.ipan.hedging.telegram.state.TelegramState;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.statetransitions.StateHandlerRegistry;
import org.ipan.hedging.telegram.statetransitions.TransitionMatcher;
import org.ipan.hedging.telegram.statetransitions.UpdateType;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class RentEnergyBot implements LongPollingSingleThreadUpdateConsumer {
    public static final String START = "/start";

    private TelegramState telegramState;
    private TelegramMessages telegramMessages;
    private UserService userService;
    private FormattingTools formattingTools;

    private final StateHandlerRegistry stateHandlerRegistry;

    @Override
    public void consume(Update update) {
        User from = getFrom(update);
        if (from.getIsBot()) {
            log.info("Ignoring update from bot: {}", from);
            // Ignore bots
            return;
        }

        Long userId = from.getId();
        UserState userState = telegramState.getOrCreateUserState(userId);
        log.info("Received update from user: username: {}, userstate: {}", from.getUserName(), userState);
        CallbackQuery callbackQuery = update.getCallbackQuery();

        if (callbackQuery != null) {
            log.info("Received update from user (callback): data: {} username: {}, userstate: {}", callbackQuery.getData(), from.getUserName(), userState);
        } else if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message.hasText()) {
                log.info("Received update from user (text): data: {}, username: {}, userstate: {}", message.getText(), from.getUserName(), userState);
            }
        }

        List<Integer> messagesToDelete = userState.getMessagesToDelete();
        if (callbackQuery != null && messagesToDelete != null && !messagesToDelete.isEmpty() && (InlineMenuCallbacks.GO_BACK.equals(callbackQuery.getData()) || InlineMenuCallbacks.TO_MAIN_MENU.equals(callbackQuery.getData()))) {
            log.info("deleting messages callback: {}, userstate {}", callbackQuery.getData(), userState );
            telegramMessages.deleteMessages(userState.getChatId(), messagesToDelete);
            userState = telegramState.updateUserState(userId, userState.withMessagesToDelete(null));
        }

        log.info("User state: {}", userState);

        AppUser user = userService.getById(userId);
        if (user != null && Boolean.TRUE.equals(user.getDisabled())) {
            log.warn("disabled user tries to access the bot  user: {}", formattingTools.formatUserForSearch(user));
            return;
        }

        if (user != null) {
            String languageCode = user.getLanguageCode();
            userState = userState.withLanguageCode(languageCode);
            TgUserLocaleHolder.setUserLocale(Locale.of(user.getLanguageCode()));

            // sync login and first name of the user. both may be null BTW.
            if (!Objects.equals(from.getUserName(), user.getTelegramUsername()) 
                || !Objects.equals(from.getFirstName(), user.getTelegramFirstName())) {
                userService.updateUser(CreateUserCommand.builder()
                .telegramId(userId)
                .username(from.getUserName())
                .firstName(from.getFirstName())
                .build());
            }
        }

        if (handleStartState(user, userState, update)) {
            if (user != null) {
                log.warn("User sent /start command user: {}, userstate: {}", formattingTools.formatUserForSearch(user), userState);
            } else {
                log.info("User registered /start command userstate: {} ", userState);
            }
            return;
        }

        if (tryRemoveNotification(userState, update)) {
            log.warn("Removing notification for user {} userState: {}", user.getTelegramUsername(), userState);
            return;
        }

        int updateType = UpdateType.NONE;
        if (update.hasCallbackQuery()) {
            updateType |= UpdateType.CALLBACK_QUERY;
        } else if (update.hasMessage()) {
            updateType |= UpdateType.MESSAGE;
        }

        List<TransitionMatcher> handlers = stateHandlerRegistry.getHandlers(userState.getState(), updateType);

        for (TransitionMatcher handler : handlers) {
            if (!handler.matches(userState, update)) {
                continue;
            }

            Object stateHandler = handler.getBean();
            Method method = handler.getMethod();

            try {
                method.invoke(stateHandler, userState, update);
            } catch (Exception e) {
                log.error("Error invoking state handler method", e);
            }
        }

        Message message = update.getMessage();

        if (message != null) {
            telegramMessages.deleteMessage(message);
        } else if (callbackQuery != null) {
            String data = callbackQuery.getData();

            if (InlineMenuCallbacks.TO_MAIN_MENU.equals(data)) {
                switch (userState.getRole()) {
                    case ADMIN:
                        telegramMessages.updateMsgToAdminMainMenu(userState, user);
                        break;
                    default:
                        telegramMessages.updateMsgToMainMenu(userState, user);
                        break;
                }
                telegramState.updateUserState(userId, userState.withState(States.MAIN_MENU));
            }
        }
    }

    private boolean tryRemoveNotification(UserState userState, Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null && InlineMenuCallbacks.NTFN_OK.equals(callbackQuery.getData())) {
            telegramMessages.deleteMessage(userState, callbackQuery);
            return true;
        }
        return false;
    }

    private boolean handleStartState(AppUser user, UserState userState, Update update) {
        Message message = update.getMessage();

        if (message != null && message.hasText()) {
            String text = message.getText();
            String part1 = "";
            String refferalLink = null;

            if (START.equals(text)) {
                part1 = text;
            } else {
                String[] split = text.split(START);
                if (split.length == 2) {
                    part1 = START;
                    refferalLink = split[1].trim();
                }
            }

            if (START.equals(part1)) {
                telegramMessages.deleteMessage(message);

                if (user == null) {
                    User from = message.getFrom();
                    String languageCode = extractLangFromUser(from);
                    user = userService.createUser(
                            CreateUserCommand.builder()
                                    .telegramId(userState.getTelegramId())
                                    .firstName(from.getFirstName())
                                    .username(from.getUserName())
                                    .languageCode(languageCode)
                                    .refferalLink(refferalLink)
                                    .build());

                } else {
                    // telegramMessages.deleteMessage(message);
/*                     role = user != null ? user.getRole() : UserRole.USER;

                    newMenuMsg = switch (role) {
                        case ADMIN -> telegramMessages.sendAdminMainMenu(userState, update.getMessage().getChatId(), user);
                        default -> telegramMessages.sendMainMenu(userState, update.getMessage().getChatId(), user);
                    }; */
                }
                Message newMenuMsg = telegramMessages.sendPromptLanguage(userState, update.getMessage().getChatId());
                UserRole role = user != null ? user.getRole() : UserRole.USER;

                // remove old menu message if exists
                if (userState.getMenuMessageId() != null) {
                    telegramMessages.deleteMessage(userState.getChatId(), userState.getMenuMessageId());
                }

                telegramState.updateUserState(userState.getTelegramId(), userState
                        .withState(States.CHOOSE_LANGUAGE)
                        .withChatId(newMenuMsg.getChatId())
                        .withRole(role)
                        .withMenuMessageId(newMenuMsg.getMessageId()));


                return true;
            }
        }
        return false;
    }

    private String extractLangFromUser(User from) {
        return switch(from.getLanguageCode()) {
            case "ru" -> "ru";
            default -> "en";
        };
    }

    private User getFrom(Update update) {
        Message message = update.getMessage();
        if (message != null && message.getFrom() != null) {
            return message.getFrom();
        }

        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null && callbackQuery.getFrom() != null) {
            return callbackQuery.getFrom();
        }

        // TODO: make custom exception for this.
        throw new IllegalStateException("Cannot determine user from update: " + update);
    }

}
