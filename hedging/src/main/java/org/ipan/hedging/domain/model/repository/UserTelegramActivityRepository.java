package org.ipan.hedging.domain.model.repository;

import org.ipan.hedging.domain.model.UserTelegramActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTelegramActivityRepository extends JpaRepository<UserTelegramActivity, Long> {
    List<UserTelegramActivity> findAllByUserIdOrderByIdDesc(Long userId);
}
