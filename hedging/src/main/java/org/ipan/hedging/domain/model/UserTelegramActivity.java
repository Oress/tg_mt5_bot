package org.ipan.hedging.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "hed_user_telegram_activity")
@Slf4j
public class UserTelegramActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_hed_user_telegram_activity")
    @SequenceGenerator(name = "seq_hed_user_telegram_activity", sequenceName = "seq_hed_user_telegram_activity", allocationSize = 1)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "current_user_state")
    private String currentUserState;

    private String type;

    private String data;

    @Column(name = "message_id")
    private Long messageId;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
