package org.ipan.hedging.cron;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ipan.hedging.domain.model.UserTelegramActivity;
import org.ipan.hedging.domain.model.repository.UserTelegramActivityRepository;
import org.ipan.hedging.telegram.States;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramAuditJob {
    private final Queue<TelegramAuditActionCommand> pendingCommands = new ConcurrentLinkedQueue<>();
    private final UserTelegramActivityRepository userTelegramActivityRepository;

    @SneakyThrows
    public void add(Long userId, String type, String data, States currentUserState) {
        if (States.ADMIN_MANAGE_USER_ACTION_PROMPT_CREDS.equals(currentUserState) && "text".equals(type)) {
            data = "****";
        }

        TelegramAuditActionCommand command = TelegramAuditActionCommand.builder()
                .userId(userId)
                .type(type)
                .data(data)
                .currentUserState(currentUserState != null ? currentUserState.name() : null)
                .createdAt(Instant.now())
                .build();
        pendingCommands.add(command);
    }

    @Transactional
    public void flushToDatabase() {
        List<UserTelegramActivity> toSave = new ArrayList<>();

        TelegramAuditActionCommand command = null;
        while((command = pendingCommands.poll()) != null) {
            UserTelegramActivity msg = new UserTelegramActivity();
            msg.setType(command.getType());
            msg.setData(command.getData());
            msg.setCreatedAt(command.getCreatedAt());
            msg.setUserId(command.getUserId());
            msg.setCurrentUserState(command.getCurrentUserState());
            toSave.add(msg);
        }

        if (!toSave.isEmpty()) {
            log.info("Flushing {} telegram audit messages to the database", toSave.size());
            userTelegramActivityRepository.saveAll(toSave);
        }
    }

    public boolean hasPendingCommands() {
        return !pendingCommands.isEmpty();
    }
}
