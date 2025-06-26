package org.ipan.hedging.telegram.views;

import java.util.ArrayList;
import java.util.List;

import org.ipan.hedging.domain.model.AppUser;
import org.ipan.hedging.domain.model.projections.EnabledInstrumentPerUser;
import org.ipan.hedging.telegram.InlineMenuCallbacks;
import org.ipan.hedging.telegram.i18n.CommonLabels;
import org.ipan.hedging.telegram.i18n.ManageUserLabels;
import org.ipan.hedging.telegram.state.UserState;
import org.ipan.hedging.telegram.utils.FormattingTools;
import org.ipan.hedging.telegram.utils.ParseUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@AllArgsConstructor
public class ManageUserActionsView {
    public static final String OPEN_BALANCE = "/balance/";

    private final TelegramClient tgClient;
    private final CommonViews commonViews;
    private final CommonLabels commonLabels;
    private final ManageUserLabels manageUserLabels;
    private final FormattingTools formattingTools;

    public void updMenuToManageUsersSearchResult(Page<AppUser> page, UserState userState) {
        String text = page.isEmpty()
                ? commonLabels.searchNoResults()
                : commonLabels.searchResults();

        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(text)
                .replyMarkup(getUsersSearchPageMarkup(page))
                .build();
        try {
            tgClient.execute(message);
        } catch (Exception e) {
            log.error("Could not updMenuToManageUsersSearchResult userstate {}", userState, e);
        }
    }

    @SneakyThrows
    public void updMenuToManageUserActionsMenu(UserState userState, AppUser appUser) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(getBalanceDescription(appUser))
                .parseMode("MARKDOWN")
                .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                .replyMarkup(getManageUserActionsMarkup(!appUser.getDisabled()))
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void changeCredsSuccess(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text("Учетные данные успешно изменены!")
                .replyMarkup(commonViews.getToMainMenuAndBackMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void userDeleted(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(manageUserLabels.deactivateSuccess())
                .replyMarkup(commonViews.getToMainMenuMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void promptAccountCreds(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text("""
                        Пожалуйста, предоставьте данные учетной записи Gerchik в следующем формате:
                        ```формат <номер_аккаунта> <пароль> ```
                        """)
                .replyMarkup(commonViews.getToMainMenuAndBackMarkup())
                .parseMode("MARKDOWN")
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void userWithTheSameAccAlreadyExists(UserState userState, String existingUser) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text("""
                        ❌Пользователь с таким аккаунт № уже существует: %s.
                        Выберете другого пользователя.

                        Пожалуйста, предоставьте данные учетной записи Gerchik в следующем формате:
                        ```формат <номер_аккаунта> <пароль> ```
                        """.formatted(existingUser))
                .replyMarkup(commonViews.getToMainMenuAndBackMarkup())
                .parseMode("MARKDOWN")
                .build();
        tgClient.execute(message);

    }

    @SneakyThrows
    public void invalidCredentials(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text("""
                        ❌Учетные данные неверные, попробуйте еще раз

                        Пожалуйста, предоставьте данные учетной записи Gerchik в следующем формате:
                        ```формат <номер_аккаунта> <пароль> ```
                        """)
                .replyMarkup(commonViews.getToMainMenuAndBackMarkup())
                .parseMode("MARKDOWN")
                .build();
        tgClient.execute(message);

    }

    @SneakyThrows
    public void confirmDeactivateUserMsg(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text(manageUserLabels.deactivateConfirm())
                .replyMarkup(commonViews.getYesNoMarkup())
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void showEnabledInstruments(UserState userState, List<EnabledInstrumentPerUser> instrumentPerUsers) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text("Инструменты, доступные пользователю:")
                .replyMarkup(instrumentPerUsersMarkup(userState.getTelegramId(), instrumentPerUsers))
                .build();
        tgClient.execute(message);
    }

    @SneakyThrows
    public void credsVerifyInProgress(UserState userState) {
        EditMessageText message = EditMessageText
                .builder()
                .chatId(userState.getChatId())
                .messageId(userState.getMenuMessageId())
                .text("Проверяем учетные данные...")
                .build();
        tgClient.execute(message);
    }

    public InlineKeyboardMarkup instrumentPerUsersMarkup(Long userId, List<EnabledInstrumentPerUser> instrumentPerUsers) {
        var builder = InlineKeyboardMarkup.builder();

        for (EnabledInstrumentPerUser instrumentPerUser : instrumentPerUsers) {
            builder.keyboardRow(
                    new InlineKeyboardRow(
                            InlineKeyboardButton
                                    .builder()
                                    .text("%s %s".formatted(
                                            instrumentPerUser.getIsEnabled() ? commonLabels.greenCircle() : commonLabels.redCircle(),
                                            instrumentPerUser.getLabel()))
                                    .callbackData(InlineMenuCallbacks.toggleInstrumentForUser(userId, instrumentPerUser.getLabel()))
                                    .build()));
        }

        return builder
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.toMainMenu())
                                        .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.goBack())
                                        .callbackData(InlineMenuCallbacks.GO_BACK)
                                        .build()))
                .build();
    }

    private String getBalanceDescription(AppUser user) {
        return manageUserLabels.preview(
                user.getTelegramId().toString(),
                ParseUtils.escapeMarkdown(FormattingTools.valOrDash(user.getTelegramUsername())),
                ParseUtils.escapeMarkdown(FormattingTools.valOrDash(user.getTelegramFirstName())),
                FormattingTools.valOrDash(user.getMt5AccNumber()),
                FormattingTools.valOrDash(user.getAccountType()),
                user.getGoogleSheetId() == null ? "Нет" : "https://docs.google.com/spreadsheets/d/"+user.getGoogleSheetId(),
                user.getDisabled() ? commonLabels.cross() : commonLabels.check()
                );
    }

    private InlineKeyboardMarkup getManageUserActionsMarkup(Boolean showDeactivateBtn) {
        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder builder = InlineKeyboardMarkup
                .builder()
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text("#️⃣ Изменить аккаунт Герчик")
                                        .callbackData(InlineMenuCallbacks.MANAGE_USER_ACTION_CHANGE_GERCHIK_CREDS)
                                        .build()))
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text("💲Изменить доступные инструменты")
                                        .callbackData(InlineMenuCallbacks.MANAGE_USER_ACTION_CHANGE_INSTRUMENTS)
                                        .build()));

        if (showDeactivateBtn) {
                builder.keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(manageUserLabels.menuDeactivate())
                                        .callbackData(InlineMenuCallbacks.MANAGE_USER_ACTION_DEACTIVATE)
                                        .build()));
        }
        return builder
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.toMainMenu())
                                        .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.goBack())
                                        .callbackData(InlineMenuCallbacks.GO_BACK)
                                        .build()))
                .build();
    }

    private InlineKeyboardMarkup getUsersSearchPageMarkup(Page<AppUser> page) {
        List<InlineKeyboardRow> users = page.getContent().stream().map(user -> {
            InlineKeyboardRow row = new InlineKeyboardRow(
                    InlineKeyboardButton
                            .builder()
                            .text(formattingTools.formatUserForSearch(user))
                            .callbackData(openBalanceRequest(user.getTelegramId()))
                            .build());
            return row;
        }).toList();

        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder<?, ?> builder = InlineKeyboardMarkup
                .builder();
        users.forEach(builder::keyboardRow);

        builder
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.searchReset())
                                        .callbackData(InlineMenuCallbacks.MANAGE_USERS_SEARCH_RESET)
                                        .build()));

        boolean hasPrev = page.hasPrevious();
        boolean hasNext = page.hasNext();

        if (hasPrev || hasNext) {
            List<InlineKeyboardButton> buttons = new ArrayList<>();
            if (hasPrev) {
                buttons.add(InlineKeyboardButton
                                .builder()
                                .text(commonLabels.searchPrevPage())
                                .callbackData(InlineMenuCallbacks.MANAGE_USERS_PREV_PAGE)
                                .build());
            }
            if (hasNext) {
                buttons.add(InlineKeyboardButton
                                .builder()
                                .text(commonLabels.searchNextPage())
                                .callbackData(InlineMenuCallbacks.MANAGE_USERS_NEXT_PAGE)
                                .build());
            }
            builder.keyboardRow(new InlineKeyboardRow(buttons));
        }

        return builder
                .keyboardRow(
                        new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.toMainMenu())
                                        .callbackData(InlineMenuCallbacks.TO_MAIN_MENU)
                                        .build(),
                                InlineKeyboardButton
                                        .builder()
                                        .text(commonLabels.goBack())
                                        .callbackData(InlineMenuCallbacks.GO_BACK)
                                        .build()))
                .build();
    }

    private String openBalanceRequest(Long userId) {
        return OPEN_BALANCE + userId;
    }
}
